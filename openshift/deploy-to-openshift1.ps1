# Create a new build config using the remote image
oc new-app registry.access.redhat.com/ubi8/openjdk-21:1.18~https://github.com/xiujungao/kafka.git `
    --name=kafka-orders-app `

# Monitor the build
# This may take a few minutes the first time, as it clones the repo, runs Maven (or Gradle if applicable), and builds the image
oc logs -f buildconfig/kafka-orders-app

# Once the build succeeds, the app will deploy as a pod. Check the status
oc get pods

# Expose a route to access the app
oc expose svc/kafka-orders-app

# Expose a route with TLS/SSL
oc create route edge kafka-orders-app --service=kafka-orders-app --port=8080

# Get the URL of the app
# NAME          HOST/PORT                             PATH   SERVICES      PORT       TERMINATION   WILDCARD
# hello-world   hello-world-jackie.apps-crc.testing          hello-world   8080-tcp                 None
# http://hello-world-jackie.apps-crc.testing/
oc get route kafka-orders-app


# after make change to the code, you can run the following command to trigger a new build
# after the buld is done, you can access the app at the URL provided by the route command
# nothing else to do, the app will be automatically redeployed
oc start-build kafka-orders-app --follow -n jackie

# To restart the deployment (if needed)
oc rollout restart deploy/kafka-orders-app -n jackie
# Monitor the deployment status
oc rollout status deploy/kafka-orders-app -n jackie


# Scale the deployment to have 2 replicas 
oc autoscale deployment kafka-orders-app  --cpu-percent=80 --min=2 --max=10