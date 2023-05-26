# todo-0: Most projects use a two stage image here where the do the building in one container and then
# do the running of the server in a different container that inherits from it. I need to probably do 
# that also in this project.
FROM node:16
WORKDIR /home/node/app
COPY package.json ./
RUN npm i
COPY . .
ENV NODE_PATH=./build
RUN npm run build
EXPOSE ${TSERVER_PORT}
ENV NODE_PATH=./build
CMD ["npm", "start"]
