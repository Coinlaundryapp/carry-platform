#!/bin/sh
# LocalStack ready hook — 미디어 업로드용 S3 버킷을 생성한다.
# 컨테이너가 준비되면(/etc/localstack/init/ready.d) 1회 실행된다. 멱등(이미 있으면 무시).
# bucket 이름은 application-local.yml의 cloud.aws.s3.bucket과 일치해야 한다.
awslocal s3 mb s3://carry-media || true
echo "localstack: s3 bucket carry-media ready"
