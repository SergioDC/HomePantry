# Notificador de SNcount para el NAS. Alpine sirve: el cliente gRPC de firebase-admin es JS puro.
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY src ./src
USER node
CMD ["node", "src/index.mjs"]
