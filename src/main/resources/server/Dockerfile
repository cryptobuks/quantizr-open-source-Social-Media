FROM node:18
WORKDIR /home/node/app
COPY package.json ./
COPY . .
EXPOSE ${TSERVER_PORT}
CMD ["npm", "start"]
