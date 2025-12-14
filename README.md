# hello-k8s-jenkins-helm

Демо-проект для Jenkins Pipeline (runner = сам Jenkins), который деплоит приложение в Kubernetes через Helm.

## Что делает
- Stage **Build**: helm lint + helm template + dry-run apply + упаковка чарта в `dist/` и архив артефактов.
- Stage **Deploy**: `helm upgrade --install --wait --atomic` в namespace `demo`.

## Jenkins credentials
Добавьте kubeconfig как **Secret file** с ID: `kubeconfig-admin`

## Открыть в браузере
Сервис NodePort: `http://<IP-ноды>:30080`
