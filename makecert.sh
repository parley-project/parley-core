# !/bin/bash
# Make a self-signed cert for testing

rm -f serverkeystore.p12
keytool -genkey -noprompt \
 -alias serverkey \
 -dname "CN=bringardner.us, OU=AA, O=BBB, L=Bringardner, S=CCCC, C=DD" \
 -keystore serverkeystore.p12 \
 -storepass changeit \
 -keypass changeit \
 -keyalg RSA \
 -keysize 2048 \
 -sigalg SHA256withRSA
