FROM node:22-alpine

WORKDIR /app

# Import extra CA certs if present (for corporate proxy environments)
COPY corporate-ca.pe[m] /tmp/
RUN if [ -f /tmp/corporate-ca.pem ]; then \
        apk add --no-cache ca-certificates && \
        mkdir -p /usr/local/share/ca-certificates && \
        cp /tmp/corporate-ca.pem /usr/local/share/ca-certificates/corporate-ca.crt && \
        update-ca-certificates && \
        echo "/usr/local/share/ca-certificates/corporate-ca.crt" > /tmp/ca-path; \
    fi

COPY package.json ./
RUN NODE_EXTRA_CA_CERTS=$(cat /tmp/ca-path 2>/dev/null || true) npm install

COPY . .

EXPOSE 5175

CMD ["sh", "-c", "if [ -f /usr/local/share/ca-certificates/corporate-ca.crt ]; then export NODE_EXTRA_CA_CERTS=/usr/local/share/ca-certificates/corporate-ca.crt; fi && npm run dev"]
