# SpendSense

An offline Android expense tracker for Indian bank SMS alerts. Open the app and enable SMS tracking to record future supported alerts. SpendSense requests RECEIVE_SMS, has no internet permission, and does not scan your inbox or save message text. Cash and unreported payments are outside its totals.

The selected month's dashboard shows income, expenses, net cash flow and spending by category. Search the month's transactions by merchant, category, type or amount. Tap a transaction to edit or delete it.

## Install

Download SpendSense.apk from the repository's Releases page using your Android phone (Android 8 or later). Android controls installation and SMS permission on each device. After installation open SpendSense once and grant SMS access.

## Build

Use JDK 17, Gradle 8.9, and Android SDK Platform 35. Run `gradle testDebugUnitTest assembleDebug`. The GitHub Actions build runs the tests, assembles the APK and verifies its signature before publishing. The first public test release uses a runner-generated debug signing key unless release secrets are configured. Builds signed with different keys cannot update each other in place. Uninstalling the app removes its local records.

For consistently signed updates, configure repository Actions secrets `SIGNING_STORE_BASE64` (base64-encoded keystore), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` before building the version you intend to keep. Each CI run uses its run number as the version code. No signing credentials belong in the repository.

## Coverage

Only new, supported INR debit and credit alerts from alphanumeric senders are considered. The SMS delivery timestamp determines the transaction date in this version. Unrecognized merchants are editable. Reference-based duplicates are ignored. Where the bank supplies no reference, the same sender, normalized message and timestamp identify redeliveries. Different deliveries without a reference can remain separate. OTP, failed, pending and promotional messages are skipped by pattern. Parsing is rule-based and does not verify that a sender is a bank.
