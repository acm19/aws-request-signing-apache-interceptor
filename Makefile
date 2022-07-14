REGION?=us-east-1
ENDPOINT?=http://my-domain.us-east-1.opensearch.localhost.localstack.cloud:4566

RELEASE_BRANCH?=release
SNAPSHOT_BRANCH?=snapshot
REMOTE?=origin

.PHONY: release
.SILENT: release
release: checkout_master verify create_release prepare_next_development_version create_pull_requests cleanup_local_branches

.PHONY: checkout_master
.SILENT: checkout_master
checkout_master:
	git checkout master
	git pull

.PHONY: create_release
.SILENT: create_release
create_release: set_release_version update_release_files_and_commit

.PHONY: set_release_version
.SILENT: set_release_version
set_release_version:
	git checkout -b $(RELEASE_BRANCH)
	mvn build-helper:parse-version versions:set -DgenerateBackupPoms=false \
	 -DnewVersion=$$\{parsedVersion.majorVersion\}.$$\{parsedVersion.minorVersion\}.$$\{parsedVersion.incrementalVersion\}

.PHONY: update_release_files_and_commit
.SILENT: update_release_files_and_commit
update_release_files_and_commit:
	DATE=$$(date +%Y\\/%m\\/%d) && \
	sed -i -E "1 s/\\s+\\(Next\\)\s*$$/ \\($$DATE\\)/" CHANGELOG.md
	VERSION=$(shell mvn help:evaluate -Dexpression=project.version -q -DforceStdout) && \
	sed -i -E "s|<version>.*<\/version>|<version>$$VERSION</version>|" README.md && \
	git add pom.xml CHANGELOG.md README.md && \
	git commit -m "Release version $$VERSION"
	git push $(REMOTE) $(RELEASE_BRANCH)

.PHONY: prepare_next_development_version
.SILENT: prepare_next_development_version
prepare_next_development_version: set_next_development_version update_development_files_and_commit

.PHONY: set_next_development_version
.SILENT: set_next_development_version
set_next_development_version:
	git checkout -b $(SNAPSHOT_BRANCH)
	mvn build-helper:parse-version versions:set -DgenerateBackupPoms=false \
	 -DnewVersion=$$\{parsedVersion.majorVersion\}.$$\{parsedVersion.minorVersion\}.$$\{parsedVersion.nextIncrementalVersion\}-SNAPSHOT

.PHONY: update_development_files_and_commit
.SILENT: update_development_files_and_commit
update_development_files_and_commit:
	VERSION=$(shell mvn help:evaluate -Dexpression=project.version -q -DforceStdout | cut -d- -f1) && \
	sed -i "1 s/^/### $$VERSION \(Next\)\\n\\n/" CHANGELOG.md && \
	git add pom.xml CHANGELOG.md && \
	git commit -m "Prepare for next development iteration $$VERSION"
	git push $(REMOTE) $(SNAPSHOT_BRANCH)

.PHONY: create_pull_requests
.SILENT: create_pull_requests
create_pull_requests:
	gh pr create --base "master" --fill --head $(RELEASE_BRANCH) --title "Release library"
	gh pr create --base "master" --fill --head $(SNAPSHOT_BRANCH) --title "Prepare for next development iteration after release" --draft

.PHONY: cleanup_local_branches
.SILENT: cleanup_local_branches
cleanup_local_branches:
	git checkout master
	git branch -D $(SNAPSHOT_BRANCH) $(RELEASE_BRANCH)

.PHONY: verify
.SILENT: verify
verify:
	mvn clean verify

.PHONY: validate_branch_dont_exist
.SILENT: validate_branch_dont_exist
validate_branch_dont_exist:
	git branch | grep -Eq "$(RELEASE_BRANCH)|$(SNAPSHOT_BRANCH)" && exit 2 || \
	git remote show $(REMOTE) | grep -Eq "$(RELEASE_BRANCH)|$(SNAPSHOT_BRANCH)" && exit 2 || true

.PHONY: run_sample
.SILENT: run_sample
run_sample:
	mvn test-compile exec:java \
		-Dexec.classpathScope=test \
		-Dexec.mainClass="io.github.acm19.aws.interceptor.test.AmazonOpenSearchServiceSample" \
		-Dexec.args="--endpoint=$(ENDPOINT) --region=$(REGION)"

.PHONY: validate_renovate_config
.SILENT: validate_renovate_config
validate_renovate_config:
	docker run -it --rm -v $$(pwd):/tmp/app  renovate/renovate bash -c "cd /tmp/app && renovate-config-validator"
