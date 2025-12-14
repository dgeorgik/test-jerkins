pipeline {
  agent any
  options {
    timestamps()
    disableConcurrentBuilds()
    skipDefaultCheckout(true)
  }

  environment {
    REPO_URL = "https://github.com/dgeorgik/test-jerkins.git"
    BRANCH   = "main"

    NAMESPACE = "demo"
    RELEASE   = "hello-k8s"
    CHART_DIR = "helm/hello-k8s"
    KUBECONFIG_CRED_ID = "kube-adm"
    HELM_VERSION = "v3.15.4"
  }

  stages {
    stage('Checkout') {
      steps {
        git branch: "${BRANCH}", url: "${REPO_URL}"
      }
    }

    stage('Build') {
      steps {
        sh '''
          #!/bin/sh
          bash -s <<BASH
          set -euo pipefail

          mkdir -p .tools/bin
          export PATH="$PWD/.tools/bin:$PATH"

          if ! command -v kubectl >/dev/null 2>&1; then
            curl -fsSL -o .tools/bin/kubectl "https://dl.k8s.io/release/$(curl -fsSL https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
            chmod +x .tools/bin/kubectl
          fi

          if ! command -v helm >/dev/null 2>&1; then
            curl -fsSL -o /tmp/helm.tgz "https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz"
            tar -zxf /tmp/helm.tgz -C /tmp
            mv /tmp/linux-amd64/helm .tools/bin/helm
            chmod +x .tools/bin/helm
          fi

          helm lint "$CHART_DIR"

          helm template "$RELEASE" "$CHART_DIR" --namespace "$NAMESPACE" \
            --set buildInfo="${BUILD_NUMBER}-${GIT_COMMIT:-nogit}" > rendered.yaml

          kubectl apply --dry-run=client -f rendered.yaml >/dev/null

          mkdir -p dist
          helm package "$CHART_DIR" -d dist
BASH
        '''
        archiveArtifacts artifacts: 'rendered.yaml,dist/*.tgz', fingerprint: true
      }
    }

    stage('Deploy') {
      steps {
        withCredentials([file(credentialsId: "${KUBECONFIG_CRED_ID}", variable: 'KUBECONFIG')]) {
          sh '''
            #!/bin/sh
            bash -s <<BASH
            set -euo pipefail

            mkdir -p .tools/bin
            export PATH="$PWD/.tools/bin:$PATH"

            if ! command -v kubectl >/dev/null 2>&1; then
              curl -fsSL -o .tools/bin/kubectl "https://dl.k8s.io/release/$(curl -fsSL https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
              chmod +x .tools/bin/kubectl
            fi

            if ! command -v helm >/dev/null 2>&1; then
              curl -fsSL -o /tmp/helm.tgz "https://get.helm.sh/helm-${HELM_VERSION}-linux-amd64.tar.gz"
              tar -zxf /tmp/helm.tgz -C /tmp
              mv /tmp/linux-amd64/helm .tools/bin/helm
              chmod +x .tools/bin/helm
            fi

            kubectl get ns "$NAMESPACE" >/dev/null 2>&1 || kubectl create ns "$NAMESPACE"

            helm upgrade --install "$RELEASE" "$CHART_DIR" \
              --namespace "$NAMESPACE" \
              --wait --timeout 5m --atomic \
              --set buildInfo="${BUILD_NUMBER}-${GIT_COMMIT:-nogit}"

            kubectl -n "$NAMESPACE" rollout status deploy/"$RELEASE" --timeout=5m
            kubectl -n "$NAMESPACE" get pods -o wide
            kubectl -n "$NAMESPACE" get svc "$RELEASE" -o wide
BASH
          '''
        }
      }
    }
  }
}
