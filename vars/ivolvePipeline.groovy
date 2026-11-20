def call(Map cfg) {
    def region   = cfg.get('region', 'eu-north-1')
    def registry = cfg.get('registry', '662371887858.dkr.ecr.eu-north-1.amazonaws.com')
    def branch   = cfg.get('branch', 'main')
    def repoUrl  = 'github.com/mohamedsala7320-sudo/CloudDevOpsProject.git'
    def ecrRepo  = cfg.ecrRepo
    def srcDir   = cfg.dir
    def image    = "${registry}/${ecrRepo}"
    def tag      = env.BUILD_NUMBER

    pipeline {
        agent any
        options { timestamps() }

        stages {
            stage('Build Image') {
                steps {
                    sh "docker build -t ${image}:${tag} ${srcDir}"
                }
            }

            stage('Scan Image') {
                steps {
                    // set --exit-code 1 to fail the pipeline when vulnerabilities are found
                    sh "trivy image --no-progress --severity HIGH,CRITICAL --exit-code 0 ${image}:${tag}"
                }
            }

            stage('Push Image') {
                steps {
                    withCredentials([usernamePassword(credentialsId: 'aws-ecr-creds',
                            usernameVariable: 'AWS_ACCESS_KEY_ID',
                            passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
                        sh """
                            aws ecr get-login-password --region ${region} | \
                              docker login --username AWS --password-stdin ${registry}
                            docker push ${image}:${tag}
                        """
                    }
                }
            }

            stage('Delete Image Locally') {
                steps {
                    sh "docker rmi ${image}:${tag} || true"
                }
            }

            stage('Update Manifests') {
                steps {
                    sh """
                        sed -i -E "s|image: .*${ecrRepo}[:@].*|image: ${image}:${tag}|" k8s/base/*.yaml
                        grep -rn "${ecrRepo}" k8s/base/
                    """
                }
            }

            stage('Push Manifests') {
                steps {
                    withCredentials([usernamePassword(credentialsId: 'github-creds',
                            usernameVariable: 'GIT_USER',
                            passwordVariable: 'GIT_PASS')]) {
                        sh """
                            git config user.name "jenkins"
                            git config user.email "jenkins@clouddevops.local"
                            git add k8s/base
                            git diff --cached --quiet && echo "No manifest changes" && exit 0
                            git commit -m "ci: ${ecrRepo} -> ${tag}"
                            git pull --rebase https://\${GIT_USER}:\${GIT_PASS}@${repoUrl} ${branch}
                            git push https://\${GIT_USER}:\${GIT_PASS}@${repoUrl} HEAD:${branch}
                        """
                    }
                }
            }
        }
    }
}
