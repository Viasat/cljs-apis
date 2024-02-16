# ClojureScript Utilities and Libraries for Common APIs

## Commands

### AWS Commands

* `cfn-desc-all`: Describe all stacks including deleted ones (up to 3 months back)
* `reach`: Run AWS
* `saws`: Call convenient AWS service/command aliases
* `saws-all`: Call AWS APIs
* `stack-run`: Run command on all EC2 instances owned by a stack (using SSM agent)

### Other Commands

* `artifactory-docker-query`: Query tags from docker repo in artifactory
* `artifactory-npm-query`: Query npm modules in artifactory
* `ghe`: Call Github APIs
* `npm-query`: Query npm modules in a Docker hub URL
* `rpm-repo-query`: Query RPM versions from a repomd-style repo
* `slackbot-test`: Demo of using viasat.apis.slack for long running Slack app/bot.
* `slack-thread-notify`: Regex match a Slack message, then update it and/or add thread to it.


## Library Modules

* `viasat.apis.artifactory`: authenticate and query artifactory images and storage.
* `viasat.apis.aws.cfn`: query and run commands on cloudformation stack instances.
* `viasat.apis.aws.core`: invoke AWS APIs and invoke lambda functions.
* `viasat.apis.github`: wrappers around Github/GHE APIs
* `viasat.apis.npm`: authenticate and query npm repositories.
* `viasat.apis.rpm`: query RPMs in a repomd-style repo.
* `viasat.apis.saws`: wrappers around AWS service commands (for the `saws` and `saws-all` commands)
* `viasat.apis.slack`: authenticate and use Slack app/bot functionality


## Command Usage

All commands will show detailed help/usage strings if called using
`--help`.

Here are a few examples for some selected commands:

### artifactory-npm-query

```
export ARTIFACTORY_BASE_URL=https://example.com/artifactory
./artifactory-npm-query repo-name
./artifactory-npm-query repo-name @group/package
```

### artifactory-docker-query

```
export ARTIFACTORY_BASE_URL=https://example.com/artifactory
./artifactory-docker-query docker-repo
./artifactory-docker-query docker-repo image
```

### cfn-desc-all

Describe all CloudFormation stacks launched in the past 3 months (the
limit of the API) and write the full JSON result to a file:

```
./cfn-desc-all stacks.json
```

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

### npm-query

Query packages in an npm registry.

* Show all versions of conlink package in `registry.npmjs.com`:

```
./npm-query conlink
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

### saws

List EC2 instances (ID, name, state, IP, key name, type, launchtime):

```
./saws ec2 list
```

Show full JSON for EC2 instances rather than summary table:

```
./saws --json ec2 list
```

List ECR repos:

```
./saws ecr repos
```

List all tags/versions (sha, push time, last pull time, size, tags) for a specific repo:

```
./saws ecr tags REPO
```

Print just tags (one tag per line) for a given REPO:

```
./saws ecr list REPO
```

List DynamoDB tables:

```
./saws db list
```

Show full JSON content of a specific DynamoDB table:

```
./saws scan TABLE
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

## Copyright & License

This software is copyright Viasat, Inc and is released under the terms
of the Eclipse Public License version 2.0 (EPL.20). A copy of the
license is located at in the LICENSE file at the top of the
repository.

