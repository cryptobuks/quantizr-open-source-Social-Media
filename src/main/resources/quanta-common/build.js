#!/usr/bin/env node

const fs = require('fs')
const esbuild = require('esbuild')

let common = {
  entryPoints: ['index.ts'],
  bundle: true,
  // minify: false,
  sourcemap: 'external'
}

esbuild
  .build({
    ...common,
    outfile: 'lib/esm/module.mjs',
    format: 'esm',
    packages: 'external'
  })
  .then(() => {
    const packageJson = JSON.stringify({type: 'module'})
    fs.writeFileSync(`${__dirname}/lib/esm/package.json`, packageJson, 'utf8')

    console.log('esm build success.')
  })

esbuild
  .build({
    ...common,
    outfile: 'lib/module.cjs.js',
    format: 'cjs',
    packages: 'external'
  })
  .then(() => console.log('cjs build success.'))

esbuild
  .build({
    ...common,
    outfile: 'lib/module.bundle.js',
    format: 'iife',
    globalName: 'QuantaCommon',
    define: {
      window: 'self',
      global: 'self',
      process: '{"env": {}}'
    }
  })
  .then(() => console.log('standalone build success.'))
