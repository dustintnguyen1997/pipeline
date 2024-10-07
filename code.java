/* Requires the Docker Pipeline plugin */
pipeline {
    agent any
    environment {
        DISABLE_AUTH = 'true'
        DB_ENGINE    = 'sqlite'
    }
    stages {
        stage('No-op') {
            steps {
                sh 'ls'
            }
        }
        stage('build') {
            steps {
                retry(3) {
                    sh './flakey-deploy.sh'
                }

                timeout(time: 3, unit: 'MINUTES') {
                    sh './health-check.sh'
                }
                echo "Database engine is ${DB_ENGINE}"
                echo "DISABLE_AUTH is ${DISABLE_AUTH}"
                sh 'printenv'
                sh './gradlew build'
                echo 'Building'
            }

        }
        stage('Test') {
            steps {
                sh 'node --version'
                sh './gradlew check'
                echo 'Testing'
            }
        }
        post {
            always {
                archiveArtifacts artifacts: 'build/libs/**/*.jar', fingerprint: true
                junit 'build/reports/**/*.xml'
                echo 'One way or another, I have finished'
                deleteDir() /* clean up our workspace */
            }
            success {
                echo 'I succeeded!'
                slackSend channel: '#ops-room',
                color: 'good',
                message: "The pipeline ${currentBuild.fullDisplayName} completed successfully."
            }
            unstable {
                echo 'I am unstable :/'
            }
            failure {
                echo 'I failed :('
                mail to: 'team@example.com',
                subject: "Failed Pipeline: ${currentBuild.fullDisplayName}",
                body: "Something is wrong with ${env.BUILD_URL}"
                hipchatSend message: "Attention @here ${env.JOB_NAME} #${env.BUILD_NUMBER} has failed.",
                color: 'RED'
            }
            changed {
                echo 'Things were different before...'
            }

        }
        stage('Deploy- Staging') {
            steps {
                echo 'Deploying'
                sh './deploy staging'
                sh './run-smoke-tests'
            }
        }
        stage('Sanity check') {
            steps {
                input "Does the staging environment look ok?"
            }
        }
        stage('Deploy - Production') {
            steps {
                sh './deploy production'
            }
        }
    }
}