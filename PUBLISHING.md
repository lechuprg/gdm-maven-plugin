# Publishing to Maven Central

This document describes the steps required to publish the GDM Maven Plugin to Maven Central
using the **new Sonatype Central Portal** (replaces the old OSSRH/Nexus process).

## Prerequisites

### 1. Create a Sonatype Central Portal Account

1. Go to **[central.sonatype.com](https://central.sonatype.com)**
2. Sign in with your **GitHub account** (`lechuprg`)
3. Your namespace `io.github.lechuprg` is automatically verified via GitHub — no JIRA ticket needed

### 2. Generate a User Token

Credentials for publishing are **tokens**, not your login password.

1. Log in to [central.sonatype.com](https://central.sonatype.com)
2. Click your profile (top right) → **"View Account"**
3. Click **"Generate User Token"**
4. Save the generated **username** and **password** — you won't see them again

### 3. Configure GPG Signing

Maven Central requires all artifacts to be signed with GPG.

#### Install GPG

**Windows:**
```powershell
# Using Chocolatey
choco install gpg4win

# Or download from https://www.gnupg.org/download/
```

**macOS:**
```bash
brew install gnupg
```

**Linux:**
```bash
sudo apt-get install gnupg
```

#### Generate a GPG Key

```bash
gpg --gen-key
```

Follow the prompts:
- Key type: RSA and RSA (default)
- Key size: 4096 bits
- Key validity: 0 (does not expire) or your preference
- Real name: Your Name
- Email: your.email@example.com
- Passphrase: Choose a strong passphrase

#### Publish Your Public Key

```bash
# List your keys
gpg --list-keys

# Send your key to a key server (use the key ID from the list)
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
```

### 4. Configure Maven Settings

Edit your `~/.m2/settings.xml` (create if it doesn't exist):

```xml
<settings>
    <servers>
        <server>
            <id>central</id>
            <username><!-- token username from central.sonatype.com --></username>
            <password><!-- token password from central.sonatype.com --></password>
        </server>
    </servers>

    <profiles>
        <profile>
            <id>release</id>
            <properties>
                <gpg.executable>gpg</gpg.executable>
                <gpg.passphrase>YOUR_GPG_PASSPHRASE</gpg.passphrase>
            </properties>
        </profile>
    </profiles>
</settings>
```

> ⚠️ The server `<id>` must be **`central`** — matching `publishingServerId` in the `central-publishing-maven-plugin` in `pom.xml`.

**Security Note:** For better security, use Maven's password encryption:
```bash
mvn --encrypt-master-password <master-password>
mvn --encrypt-password <token-password>
```

## Publishing Process

### Deploy a SNAPSHOT Version

> ⚠️ The Central Portal's `central-publishing-maven-plugin` does **not** support SNAPSHOTs.
> SNAPSHOTs are deployed directly via `maven-deploy-plugin` to the snapshot repository.

```bash
mvn clean deploy
```

SNAPSHOT artifacts go to:
`https://central.sonatype.com/repository/maven-snapshots/`

### Deploy a Release Version

1. **Update version** (remove `-SNAPSHOT`):
   ```bash
   mvn versions:set -DnewVersion=1.0.0
   mvn versions:commit
   ```

2. **Deploy with the release profile** (includes GPG signing + Central Portal upload):
   ```bash
   mvn clean deploy -Prelease
   ```

3. **Or use the Maven Release Plugin** (recommended for proper releases):
   ```bash
   mvn release:clean release:prepare release:perform -Prelease
   ```

### After Deploying a Release

With `autoPublish=true` in `central-publishing-maven-plugin`, the artifact is **automatically submitted** to Maven Central after upload. You can monitor the status at:

1. Log in to [central.sonatype.com](https://central.sonatype.com)
2. Go to **"Deployments"** to see the status
3. Artifacts appear on [search.maven.org](https://search.maven.org) within ~30 minutes

## Troubleshooting

### Common Issues

1. **405 Not Allowed**
   - You're still using the old `s01.oss.sonatype.org` URL — update `pom.xml` to use `central-publishing-maven-plugin`

2. **GPG signing fails**
   - Ensure GPG is installed and in your PATH
   - Check that your key is available: `gpg --list-secret-keys`
   - On Windows with GPG4Win, you may need: `gpg --batch --yes --passphrase "..." ...`

3. **401 Unauthorized**
   - Use the **token** from [central.sonatype.com](https://central.sonatype.com), not your login password
   - Ensure the `<server><id>` in `settings.xml` is `central`

4. **Missing artifacts**
   - Maven Central requires: JAR, sources JAR, javadoc JAR, POM, and GPG signatures for all
   - Ensure `maven-source-plugin` and `maven-javadoc-plugin` are in the `release` profile

5. **Namespace not verified**
   - For `io.github.lechuprg`, sign in to Central Portal with GitHub — it auto-verifies

### Useful Commands

```bash
# Verify your setup without deploying
mvn clean verify -Prelease

# Check GPG agent
gpg-connect-agent /bye

# Export public key for verification
gpg --armor --export YOUR_KEY_ID > public-key.asc

# List secret keys
gpg --list-secret-keys --keyid-format LONG
```

## Reference

- [Maven Central Portal](https://central.sonatype.com)
- [Central Publishing Maven Plugin](https://central.sonatype.org/publish/publish-portal-maven/)
- [Migrating from OSSRH](https://central.sonatype.org/publish/publish-portal-ossrh-migration/)
- [Maven GPG Plugin](https://maven.apache.org/plugins/maven-gpg-plugin/)
