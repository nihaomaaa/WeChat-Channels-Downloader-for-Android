FROM node:24-alpine
WORKDIR /app
COPY package.json worker.mjs node-server.mjs ./
USER node
ENV HOST=0.0.0.0
EXPOSE 8787
CMD ["node","node-server.mjs"]

