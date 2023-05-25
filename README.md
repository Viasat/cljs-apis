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

## Command Usage

### ghe

Refer to the API reference at https://octokit.github.io/rest.js/v19

* Get all orgs as formatted JSON:

```
./ghe orgs list | jq '.' | less
```

* Show number of repos in `foo` org:

```
./ghe repos listForOrg org=foo | jq 'length'
```

* Return (JSON) all open pull requests for `foo/bar` repo in tabular form:

```
./ghe --owner foo --repo bar pulls list state=open
```

* List (in tabular form) all workflows for `bar` repo:

```
./ghe --table --owner foo --repo bar actions listRepoWorkflows
```

* List (in tabular form) all workflow runs for `bar` repo workflow ID 2129:

```
./ghe --table --owner foo --repo bar actions listWorkflowRuns workflow_id=2129
```

### rpm-repo-query

* List CentOS 8 extra RPM packages:

```
./rpm-repo-query http://mirror.centos.org/centos-8/8/extras/x86_64/os/
RPM_REPO_BASE_URL=http://mirror.centos.org ./rpm-repo-query centos-8/8/extras/x86_64/os/
```

* Filter to only versions for a single RPM package:

```
./rpm-repo-query centos-8/8/extras/x86_64/os/ centos-release-stream
```


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
