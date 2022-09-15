REGION?=us-east-1
ENDPOINT?=http://my-domain.us-east-1.opensearch.localhost.localstack.cloud:4566

.PHONY: verify
.SILENT: verify
verify:
	mvn clean verify

.PHONY: run_sample
.SILENT: run_sample
run_sample:
	mvn test-compile exec:java \
		-Dexec.classpathScope=test \
		-Dexec.mainClass="io.github.acm19.aws.interceptor.test.AmazonOpenSearchServiceSample" \
		-Dexec.args="--endpoint=$(ENDPOINT) --region=$(REGION)"

.PHONY: run_v5_sample
.SILENT: run_v5_sample
run_v5_sample:
	mvn test-compile exec:java \
		-Dexec.classpathScope=test \
		-Dexec.mainClass="io.github.acm19.aws.interceptorv5.test.AmazonOpenSearchServiceSample" \
		-Dexec.args="--endpoint=$(ENDPOINT) --region=$(REGION)"

debug_v5_sample:
	mvn exec:exec -Dexec.executable="java" -Dexec.classpathScope=test \
		-Dexec.args="-classpath %classpath -Xdebug \
				-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005 \
				io.github.acm19.aws.interceptorv5.test.AmazonOpenSearchServiceSample --endpoint=$(ENDPOINT) --region=$(REGION)"

.PHONY: validate_renovate_config
.SILENT: validate_renovate_config
validate_renovate_config:
	docker run -it --rm -v $$(pwd):/tmp/app  renovate/renovate bash -c "cd /tmp/app && renovate-config-validator"
