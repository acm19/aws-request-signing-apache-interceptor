#!/usr/bin/env python3

from aws_cdk import App, DefaultStackSynthesizer
from opensearch_cluster.opensearch_cluster import OpenSearchStack

app = App(analytics_reporting=False)

# # Replace the account and region with your information
# env = core.Environment(account="You account information", region="cn-northwest-1")
# Ec2CloudwatchStack(app, "ec2-cloudwatch", env=env)

OpenSearchStack(app,
                "opensearch-cluster",
                synthesizer=DefaultStackSynthesizer(generate_bootstrap_version_rule=False))
app.synth()
