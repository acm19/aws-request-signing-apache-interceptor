from aws_cdk import CfnOutput, Stack, aws_opensearchservice, RemovalPolicy
from aws_cdk.aws_opensearchservice import EngineVersion, EncryptionAtRestOptions, EbsOptions
from constructs import Construct


class OpenSearchStack(Stack):

    def __init__(self, scope: Construct, id: str, **kwargs) -> None:
        super().__init__(scope, id, **kwargs)

        open_search_cluster = aws_opensearchservice.Domain(self,
                                                           "InterceptorTest",
                                                           version=EngineVersion.OPENSEARCH_1_2,
                                                           capacity=aws_opensearchservice.CapacityConfig(
                                                               data_node_instance_type="t3.small.search"
                                                           ),
                                                           ebs=EbsOptions(
                                                               volume_size=10
                                                           ),
                                                           node_to_node_encryption=True,
                                                           encryption_at_rest=EncryptionAtRestOptions(
                                                               enabled=True
                                                           ),
                                                           removal_policy=RemovalPolicy.DESTROY)

        # output information after deploy
        output = CfnOutput(self, "OpenSearch Endpoint",
                                value=open_search_cluster.domain_endpoint,
                                description="Endpoint to connect to OpenSearch")
