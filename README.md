# SpendSense Insights

An offline Android spending journal. The overview shows net cash flow, income and expenses for the selected dates. Tap a category in the spending breakdown to see its payments. Spending trends show daily, weekly or monthly totals according to the length of the range. The Activity tab supports search, expense/income filters, editing and deletion. Use the plus button to record cash or an unreported payment.

## Using the app

Download SpendSense.apk from Releases on an Android 8 or newer phone. The dashboard opens on the current month through today. Tap the dates beside Overview to select an inclusive start and end, including historical years. One selection covers up to six calendar months. Date filtering uses local calendar days.

Enable SMS to record supported incoming bank alerts. To backfill a selected period, tap **Import bank SMS for this range**. This optional action requests inbox permission and processes messages on the phone. Only recognized transactions and duplicate fingerprints are stored. Import results depend on retained SMS, supported formats and permission availability. Historical edits from another installation cannot be recovered from SMS. The app has no internet permission.

The first Insights test APK installs alongside the original SpendSense build to preserve the original app and its local data. Each installation has a separate ledger and SMS permission. Re-importing identical alerts within Insights skips matching fingerprints. For alerts without a bank reference, differences in delivery timestamps between the live SMS and the inbox can prevent duplicate matching.

## Development

Use JDK 17, Gradle 8.9 and Android SDK Platform 35. Run `gradle testDebugUnitTest assembleDebug`. The GitHub workflow also tests dashboard interactions in headless Chrome and verifies the APK signature before publishing.

Debug builds use application ID dev.spendsense.insights. A hosted runner generates a temporary debug key. For in-place updates use a consistent signing key and application ID. The release configuration accepts repository Actions secrets SIGNING_STORE_BASE64 (encoded keystore), SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS and SIGNING_KEY_PASSWORD. Keep private keys outside the repository. Uninstalling an app deletes its ledger.

Transaction classification is rule-based. Tap a row to correct the merchant or category. Balance notices, authentication codes and failed payments are filtered by pattern. SpendSense does not verify sender identity, connect to a bank account or compute its balance.
