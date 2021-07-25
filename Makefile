validate_renovate_config:
	docker run -it --rm -v $$(pwd):/tmp/app  renovate/renovate bash -c "cd /tmp/app && renovate-config-validator"	
