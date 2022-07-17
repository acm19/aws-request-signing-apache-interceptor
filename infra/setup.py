import setuptools


with open("README.md") as fp:
    long_description = fp.read()


setuptools.setup(
    name="opensearch",
    version="0.0.1",

    description="Testing OpenSearch Interceptor project.",
    long_description=long_description,
    long_description_content_type="text/markdown",

    author="acm19",

    package_dir={"": "opensearch_cluster"},
    packages=setuptools.find_packages(where="opensearch_cluster"),

    python_requires=">=3.6",

    classifiers=[
        "Development Status :: 4 - Beta",

        "Intended Audience :: Developers",

        "License :: OSI Approved :: Apache Software License",

        "Programming Language :: Python :: 3 :: Only",
        "Programming Language :: Python :: 3.6",
        "Programming Language :: Python :: 3.7",
        "Programming Language :: Python :: 3.8",
        "Programming Language :: Python :: 3.9",

        "Topic :: Software Development :: Code Generators",
        "Topic :: Utilities",

        "Typing :: Typed",
    ],
)
