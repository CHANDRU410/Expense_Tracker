# SpendSense Insights

An offline Android spending journal. The overview shows net cash flow, income and expenses for the selected dates. Tap a category in the spending breakdown to see its payments. Spending trends show daily, weekly or monthly totals according to the length of the range. The Activity tab supports search, expense/income filters, editing and deletion. Use the plus button to record cash or an unreported payment.

## Using the app

Download SpendSense.apk from Releases on an Android 8 or newer phone. The dashboard opens on the current month through today. Tap the dates beside Overview to select an inclusive start and end, including historical years. One selection covers up to six calendar months. Date filtering uses local calendar days.

Enable SMS to record supported incoming bank alerts. To backfill a selected period, tap **Import bank SMS for this range**. This optional action requests inbox permission and processes messages on the phone. Only recognized transactions and duplicate fingerprints are stored. Import results depend on retained SMS, supported formats and permission availability. Historical edits from another installation cannot be recovered from SMS. The app has no internet permission.

## Installing and updating

The two earlier test builds used disposable signing keys. Keep them installed if they contain records. The first permanent release installs once alongside those test builds. Its package is dev.spendsense.mobile. Subsequent stable releases update the same app and keep its private ledger. In SpendSense, tap **Check for updates**, open the newest stable release and tap SpendSense.apk. The permanent download address is https://github.com/CHANDRU410/Expense_Tracker/releases/latest/download/SpendSense.apk after the first stable release is published.

## One-time release setup on Windows

Download tools/setup-signing.ps1, inspect it, and run it in Windows PowerShell using your normal account. It uses Windows certificate tools and needs no Android Studio. It creates a release signing certificate and keeps an encrypted PFX and Windows-account-protected password under your local application data folder SpendSenseSigning. Retain an independent secure backup of the signing certificate and password for recovery. Rerun the script on the same Windows account and PC to reuse that key. Never post the secret in a chat, issue, or source file.

With an authenticated GitHub CLI the script sets the repository secret and starts the build. Otherwise follow its prompts to paste the signing bundle into the GitHub Actions repository secret SPENDSENSE_SIGNING_BUNDLE and manually run the workflow on main. CI builds and checks the APK without a repository secret but publishes only when the permanent signing credentials are present. After the first stable release CI checks the previous release certificate fingerprint before publishing. Version codes increase with workflow run numbers.

Use JDK 17, Gradle 8.9 and Android SDK Platform 35 for local development. The app's parser is rule-based. Edits and manual entries in the old test apps will remain in those apps. Import supported bank SMS into the permanent app to rebuild SMS-based history.
