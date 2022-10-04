# ClojureScript Utilities and Libraries for Common APIs

## Commands

* `artifactory-docker-query`: Query tags from docker repo in artifactory 
* `rpm-repo-query`: Query RPM versions from a repomd-style repo
* `ghe`: Call Github APIs
* `saws`: Call convenient AWS service/command aliases
* `saws-all`: Call AWS APIs
* `stack-run`: Run command on all EC2 instances owned by a stack (using SSM agent)

## Library Modules

* `viasat.apis.aws.core`: general functions for invoking AWS APIs and invoking lambda functions.
* `viasat.apis.aws.cfn`: functions for querying and running commands on stack instances.
* `viasat.apis.saws`: wrappers around AWS service commands (for the `saws` and `saws-all` commands)
* `viasat.apis.artifactory`: functions for authenticating and querying artifactory images and storage.
* `viasat.apis.rpm`: functions for querying RPMs in a repomd-style repo.
* `viasat.apis.github`: functions for all Github/GHE APIs


## Build JS code

Use shadow-cljs to build the JS code in the `dist/` directory.

```
npx shadow-cljs compile cljs-apis
```

## Publish npm module

Build the JS code as above and then run:

```
npm run dist-publish
```

## Local development

If you are developing a project that depends on this one, you can
quickly package and then install it in the dependent project.

First compile with shadow-cljs as above and then create/update a npm
tarball in this repo:

```
npm run dist-pack
```

Then in the dependent project run install pointing to the tarball:

```
npm install ../path/to/cljs-apis/viasat-cljs-apis-0.0.1.tgz
```
