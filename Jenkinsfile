#!groovy

pipeline {
    environment {
        JAVA_HOME = "/opt/java/jdk-17.0.2/"
        CI = "false"
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
        DO_API_TOKEN = vault path: 'jenkins/digitalocean', key: 'ro_token'
    }
    options {
        disableConcurrentBuilds()
    }
    agent any
    stages {
        stage('Prepare build') { 
            steps {
                script {
                    sh 'cp ./src/main/resources/bidder-config/alkimi.yaml.${MY_ENV} ./src/main/resources/bidder-config/alkimi.yaml'
                }
            }
        }
	    stage('Build') {
            steps {
                script {
                    sh "echo ${BRANCH_NAME} ${GIT_BRANCH} ${GIT_COMMIT} ${MY_VERSION} ${MY_ENV}"
			        sh "mvn clean package -Dmaven.test.skip=true -Drevision=${MY_VERSION}"
                }
            }
        }
        stage('Deploy to dev') {
            when {
                branch "master"
            }
            steps {
                git branch: 'master', url: "git@github.com:Alkimi-Exchange/alkimi-ansible.git", credentialsId: 'ssh-alkimi-ansible'
                sh "ansible-playbook ./apps/dev/prebid-server.yml --extra-vars='artifactPath=${env.WORKSPACE}/target/prebid-server.jar configPath=${env.WORKSPACE}/config'"
            }
        }
    }
}
