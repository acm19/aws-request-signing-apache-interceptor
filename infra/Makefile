BOOTSTRAP_STACK_NAME?=CDKToolkit

.SILENT: venv
venv:
	python3 -m venv venv
	. venv/bin/activate && \
	pip install -r requirements.txt

.PHONY: clean
.SILENT: clean
clean:
	rm -rf venv

.SILENT: node_modules
node_modules:
	npm install

.PHONY: synth
.SILENT: synth
synth: node_modules
	./node_modules/.bin/cdk synth --path-metadata false

.PHONY: bootstrap
.SILENT: bootstrap
bootstrap: node_modules venv
	. venv/bin/activate && \
	./node_modules/.bin/cdk bootstrap

.PHONY: deploy
.SILENT: deploy
deploy: node_modules venv
	. venv/bin/activate && \
	./node_modules/.bin/cdk deploy --path-metadata false

.PHONY: destroy
.SILENT: destroy
destroy:
	. venv/bin/activate && \
	./node_modules/.bin/cdk destroy

.PHONY: destroy_all
.SILENT: destroy_all
destroy_all: node_modules destroy
	./node_modules/.bin/cdk ls > /dev/null 2>&1 || (echo "Being in Virtual Env is necessary to run this target"; exit 1)
	BUCKET_NAME=$$(aws cloudformation describe-stacks --stack-name $(BOOTSTRAP_STACK_NAME) --query 'Stacks[0].Outputs[?OutputKey==`BucketName`].OutputValue' --output=text); \
	aws s3api list-object-versions --bucket "$$BUCKET_NAME" --query='Versions[].Key' --output=text | grep -ve '^None$$' \
		&& aws s3api delete-objects --bucket $$BUCKET_NAME \
			--delete "$$(aws s3api list-object-versions --bucket "$$BUCKET_NAME" --output=json --query='{Objects: Versions[].{Key:Key,VersionId:VersionId}}')" \
		|| echo "No object versions to delete"; \
	aws s3api list-object-versions --bucket "$$BUCKET_NAME" --query='DeleteMarkers[].Key' --output=text | grep -ve '^None$$' \
		&& aws s3api delete-objects --bucket $$BUCKET_NAME \
			--delete "$$(aws s3api list-object-versions --bucket "$$BUCKET_NAME" --output=json --query='{Objects: DeleteMarkers[].{Key:Key,VersionId:VersionId}}')" \
		|| echo "No object markers to delete"; \
	aws s3 rb --force s3://$$BUCKET_NAME
	aws cloudformation delete-stack --stack-name $(BOOTSTRAP_STACK_NAME)
	aws cloudformation wait stack-delete-complete --stack-name $(BOOTSTRAP_STACK_NAME)
