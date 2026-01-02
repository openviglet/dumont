set TAG_NAME=v2026.1
mvn build-helper:parse-version versions:set -DnewVersion=${parsedVersion.majorVersion}.${parsedVersion.minorVersion}.${parsedVersion.incrementalVersion}.${parsedVersion.nextBuildNumber} versions:commit
mvn clean package
rem gh release create v2026.1 --generate-notes
gh release upload %TAG_NAME% aem\aem-plugin\target\aem-plugin.jar --clobber
gh release upload %TAG_NAME% connector\connector-app\target\dumont-connector.jar --clobber
gh release upload %TAG_NAME% db\db-app\target\dumont-db.jar --clobber
gh release upload %TAG_NAME% filesystem\fs-connector\target\dumont-filesystem.jar --clobber
gh release upload %TAG_NAME% web-crawler\wc-plugin\target\web-crawler-plugin.jar --clobber