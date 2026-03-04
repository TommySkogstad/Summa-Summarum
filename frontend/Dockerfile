FROM node:22-alpine AS build

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

COPY package.json package-lock.json* ./
RUN NODE_EXTRA_CA_CERTS=$(cat /tmp/ca-path 2>/dev/null || true) npm install

COPY . .
RUN npm run build

FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 5175
