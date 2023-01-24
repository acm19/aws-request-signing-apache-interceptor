
# Integration Tests

This project creates an OpenSearch cluster on AWS for development purposes. Its main objective is to enable collaborators to easily test features against real cluster.

To manually create a virtualenv and install the dependencies on MacOS and Linux:

```
make venv
```

After the init process completes and the virtualenv is created, you can use the following step to activate your virtualenv.

```
source ./venv/bin/activate
```

At this point you can now synthesise the CloudFormation template for this code.

```
make synth
```

## Operations with the infrastructure

To deploy the cluster make sure you have bootstrapped your CDK if necessary:

```
make bootstrap
```

From this on you can just run the deployment command:

```
make deploy
```

Once you have finished your work you can destroy the infrastructure so no additional charges are applied:

```
make destroy
```

In case you want to get rid of everything, including the bootstrap stack use instead:

```
make destroy_all
```

## Useful commands

 * `cdk ls`          list all stacks in the app
 * `cdk synth`       emits the synthesized CloudFormation template
 * `cdk deploy`      deploy this stack to your default AWS account/region
 * `cdk diff`        compare deployed stack with current state
 * `cdk docs`        open CDK documentation

Enjoy!
