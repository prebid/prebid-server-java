#!groovy
pipeline {
    environment {
        CI = "false"
        MY_VERSION = sh(
                script: 'echo "${BRANCH_NAME}.${BUILD_ID}"',
                returnStdout: true
        ).trim()
    }
    options {
        disableConcurrentBuilds()
    }
    agent any
    stages {
        stage('Build jar') {
            steps {
                script {
                        sh "echo ${BRANCH_NAME} ${GIT_BRANCH} ${GIT_COMMIT} ${JAVA_HOME} ${MY_VERSION}"
                        sh "mvn clean package -Drevision=${MY_VERSION}"
                }
            }
        }
        stage('Build and push docker images') {
            steps {
                script {
                     docker.withRegistry('https://685748726849.dkr.ecr.eu-west-2.amazonaws.com','ecr:eu-west-2:jenkins_ecr') {
                         def dockerImage = docker.build("alkimi/prebid-server:${MY_VERSION}", "-f Dockerfile ${WORKSPACE}")
                         dockerImage.push()
                         dockerImage.push('latest')
                     }
                }
            }
        }
    }
}
