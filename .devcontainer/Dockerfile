# From https://github.com/microsoft/vscode-dev-containers/blob/master/containers/go/.devcontainer/Dockerfile
ARG VARIANT="17-jdk-bookworm"
FROM mcr.microsoft.com/vscode/devcontainers/java:${VARIANT}

# [Optional] Uncomment this section to install additional OS packages.
RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
    && apt-get -y install --no-install-recommends vim

# [Optional] Uncomment the next line to use go get to install anything else you need
# RUN go get -x <your-dependency-or-tool>
