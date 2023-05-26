# Quanta NodeJS Server

Quanta runs this NodeJS server alongside the SpringBoot/Java server. Currently the only reason for this NodeJS server is so that we can have all our Nostr code be in pure TypeScript, or at least the parts of it related to curating a public feed. All of the E2E Encryption, Signing, and Keys is still done *only* in the browser.

Future Plans: Eventually there will be the capability of running a "Quanta Lite" version of Quanta where the NodeJS server may be the *only* server side component running (no Java SpringBoot at all), and it will be able to have a small subset of Quanta capabilities that are available in the Java server.

### Start the dev server

`npm run dev`

### Build and Run Project

NOTE: `npm i` is only needed to update node_modules if new packages are installed.

`npm i`
`npm run build`
`npm start`
