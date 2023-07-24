#!groovy

pipeline {
    environment {
        JAVA_HOME = "/opt/jdk-17.0.2"
        MY_ENV = sh(returnStdout: true, script:
                '''#!/bin/bash
                   if [[ $BRANCH_NAME =~ "release-" ]]; then echo prod; else echo dev; fi
                '''
        ).trim()
        MY_VERSION = sh(returnStdout: true, script:
                '''#!/bin/bash
                   if [[ $BRANCH_NAME =~ "release-" ]]; then echo "${BRANCH_NAME}.${BUILD_ID}"; else echo "${BRANCH_NAME}.${BUILD_ID}-SNAPSHOT"; fi
                '''
        ).trim()
    }
    options {
        disableConcurrentBuilds()
    }
    agent any
    stages {
	    stage('Build') {
            steps {
                script {
                    sh "echo ${BRANCH_NAME} ${GIT_BRANCH} ${GIT_COMMIT} ${MY_VERSION} ${MY_ENV}"
			        sh "mvn clean package -Dbuild.version=${MY_VERSION}"
                }
            }
        }
        stage('Publish') {
            steps {
                script {
   		            sh "mvn deploy -Dbuild.version=${MY_VERSION}"
                }
            }
        }
    }
}
