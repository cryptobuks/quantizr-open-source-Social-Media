# Quanta NodeJS Server

Quanta will eventually have a NodeJS server running alongside the SpringBoot/Java server, and this project is the beginnings of this capability. The reason for this is primarily so that all our new Nostr code that needs to be able to run server-side can be written in TypeScript instad of Java.

Also eventually there will be the capability of running a "Quanta Lite" version of Quanta where the NodeJS server may be the *only* server side component running, and it will be able to have a small subset of Quanta
capabilities, but still be a runnable "Quata" app.

### Start the dev server

`npm run dev`

### Build and Run Project

NOTE: `npm i` is only needed to update node_modules if new packages are installed.

`npm i`
`npm run build`
`npm start`
