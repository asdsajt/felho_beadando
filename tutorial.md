# initial configs
gsutil mb gs://kswov1_image_bucket_v2
gsutil mb gs://kswov1_result_bucket_v2
gcloud pubsub topics create kswov1_translate_topic

# clone the repo
git clone https://github.com/asdsajt/felho_beadando.git

# go to ocr-process-image folder
cd felho_beadando/ocr/ocr-process-image/
cd ../ocr-translate-text/

# deploying the function
gcloud functions deploy ocr-extract \
--entry-point functions.OcrProcessImage \
--runtime java11 \
--memory 512MB \
--trigger-bucket kswov1_image_bucket_v2 \
--set-env-vars "^:^GCP_PROJECT=kswov1-beadando-313911:TRANSLATE_TOPIC=kswov1_translate_topic:TO_LANG=hu"

# go to ocr-translate-text folder
cd ../ocr-translate-text/

# deploying the function
gcloud functions deploy ocr-translate \
--entry-point functions.OcrTranslateText \
--runtime java11 \
--memory 512MB \
--trigger-topic kswov1_translate_topic \
--set-env-vars "GCP_PROJECT=kswov1-beadando-313911,RESULT_BUCKET=kswov1_result_bucket_v2"

