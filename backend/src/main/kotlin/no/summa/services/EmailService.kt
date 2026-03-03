package no.summa.services

import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import no.summa.database.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.Properties
import java.util.concurrent.locks.ReentrantLock

class EmailService {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)
    private val devMode = System.getenv("DEV_MODE")?.toBoolean() ?: true

    private var smtpHost: String = ""
    private var smtpPort: Int = 25
    private var smtpUser: String = ""
    private var smtpPassword: String = ""
    private var smtpFrom: String = "noreply@summa.tommytv.no"
    private var smtpDomain: String = ""
    private var requireAuth: Boolean = false

    private val sendLock = ReentrantLock()
    private var lastSendTime = 0L
    private val minIntervalMs = 100L

    fun configure(
        host: String,
        port: Int,
        user: String = "",
        password: String = "",
        from: String = "noreply@summa.tommytv.no",
        domain: String = "",
        requireAuth: Boolean = false
    ) {
        this.smtpHost = host
        this.smtpPort = port
        this.smtpUser = user
        this.smtpPassword = password
        this.smtpFrom = from
        this.smtpDomain = domain
        this.requireAuth = requireAuth
        logger.info("E-post konfigurert: host=$host, port=$port, from=$from, auth=$requireAuth")
    }

    fun isConfigured(): Boolean = smtpHost.isNotBlank()

    fun sendEmail(
        to: String,
        subject: String,
        body: String,
        type: EmailType,
        forceDelivery: Boolean = false
    ): Int = transaction {
        val emailId = EmailLog.insertAndGetId {
            it[toEmail] = to
            it[fromEmail] = smtpFrom
            it[EmailLog.subject] = subject
            it[EmailLog.body] = body
            it[EmailLog.type] = type
            it[status] = EmailStatus.PENDING
        }.value

        if (devMode && !forceDelivery) {
            logger.info("=" .repeat(60))
            logger.info("DEV MODE - E-post (logget, ikke levert)")
            logger.info("Til: $to")
            logger.info("Emne: $subject")
            logger.info("-".repeat(60))
            logger.info(body)
            logger.info("=".repeat(60))

            EmailLog.update({ EmailLog.id eq emailId }) {
                it[status] = EmailStatus.SKIPPED_DEV
            }
        } else if (devMode && forceDelivery) {
            logger.info("=" .repeat(60))
            logger.info("DEV MODE - E-post med forceDelivery (type: ${type.name})")
            logger.info("Til: $to")
            logger.info("Emne: $subject")
            logger.info("-".repeat(60))
            logger.info(body)
            logger.info("=".repeat(60))

            if (isConfigured()) {
                try {
                    sendSmtp(to, subject, body)
                    EmailLog.update({ EmailLog.id eq emailId }) {
                        it[status] = EmailStatus.SENT
                        it[sentAt] = TimeUtils.nowOslo()
                    }
                } catch (e: Exception) {
                    EmailLog.update({ EmailLog.id eq emailId }) {
                        it[status] = EmailStatus.SKIPPED_DEV
                        it[errorMessage] = "Dev: SMTP feilet (${e.message})"
                    }
                    logger.info("Dev: SMTP-levering feilet for $to: ${e.message}")
                }
            } else {
                EmailLog.update({ EmailLog.id eq emailId }) {
                    it[status] = EmailStatus.SKIPPED_DEV
                    it[errorMessage] = "Dev: SMTP ikke konfigurert"
                }
            }
        } else if (!isConfigured()) {
            logger.warn("E-post ikke konfigurert - hopper over sending til $to")
            EmailLog.update({ EmailLog.id eq emailId }) {
                it[status] = EmailStatus.SKIPPED_DEV
            }
        } else {
            try {
                sendSmtp(to, subject, body)
                EmailLog.update({ EmailLog.id eq emailId }) {
                    it[status] = EmailStatus.SENT
                    it[sentAt] = TimeUtils.nowOslo()
                }
                logger.info("E-post sendt til $to via $smtpHost:$smtpPort")
            } catch (e: Exception) {
                EmailLog.update({ EmailLog.id eq emailId }) {
                    it[status] = EmailStatus.FAILED
                    it[errorMessage] = e.message
                }
                logger.error("Feil ved sending av e-post til $to: ${e.message}", e)
            }
        }

        emailId
    }

    private fun sendSmtp(to: String, subject: String, body: String) {
        sendLock.lock()
        try {
            val now = System.currentTimeMillis()
            val elapsed = now - lastSendTime
            if (elapsed < minIntervalMs) {
                Thread.sleep(minIntervalMs - elapsed)
            }
            lastSendTime = System.currentTimeMillis()
        } finally {
            sendLock.unlock()
        }

        val props = Properties().apply {
            put("mail.smtp.host", smtpHost)
            put("mail.smtp.port", smtpPort.toString())
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "10000")
            put("mail.smtp.writetimeout", "10000")

            if (requireAuth) {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
            } else {
                put("mail.smtp.auth", "false")
            }
        }

        val session = if (requireAuth && smtpUser.isNotBlank()) {
            Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(smtpUser, smtpPassword)
                }
            })
        } else {
            Session.getInstance(props)
        }

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(smtpFrom, "Summa Summarum"))
            setRecipient(Message.RecipientType.TO, InternetAddress(to))
            setSubject(subject, "UTF-8")
            setText(body, "UTF-8")
        }

        Transport.send(message)
    }

    fun sendOtpEmail(email: String, otp: String) {
        val subject = "Din innloggingskode for Summa Summarum"
        val body = """
            Hei,

            Din engangskode for innlogging er: $otp

            Koden er gyldig i 10 minutter.

            Med vennlig hilsen,
            Summa Summarum
        """.trimIndent()

        sendEmail(
            to = email,
            subject = subject,
            body = body,
            type = EmailType.OTP,
            forceDelivery = true
        )
    }
}
