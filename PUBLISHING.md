# Publishing to Maven Central

This document describes the steps required to publish the GDM Maven Plugin to Maven Central.

## Prerequisites

### 1. Create a Sonatype Account

1. Register at [Sonatype JIRA](https://issues.sonatype.org/secure/Signup!default.jspa)
2. Create a New Project ticket requesting access to your groupId (`org.example.gdm`)
3. Wait for approval (usually 1-2 business days)

**Note:** If you're using a domain you own (e.g., `com.yourcompany`), you'll need to verify domain ownership. If using GitHub-based coordinates (e.g., `io.github.yourusername`), Sonatype will verify your GitHub account.

### 2. Configure GPG Signing

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

### 3. Configure Maven Settings

Edit your `~/.m2/settings.xml` (create if it doesn't exist):

```xml
<settings>
    <servers>
        <server>
            <id>ossrh</id>
            <username>YOUR_SONATYPE_USERNAME</username>
            <password>YOUR_SONATYPE_PASSWORD</password>
        </server>
    </servers>

    <profiles>
        <profile>
            <id>ossrh</id>
            <activation>
                <activeByDefault>true</activeByDefault>
            </activation>
            <properties>
                <gpg.executable>gpg</gpg.executable>
                <gpg.passphrase>YOUR_GPG_PASSPHRASE</gpg.passphrase>
            </properties>
        </profile>
    </profiles>
</settings>
```

**Security Note:** For better security, use Maven's password encryption:
```bash
mvn --encrypt-master-password <master-password>
mvn --encrypt-password <sonatype-password>
```

## Update Project Information

Before publishing, update the `pom.xml` with your actual information:

1. **URL**: Replace `YOUR_USERNAME` with your GitHub username
2. **Developer Info**: Update `YOUR_ID`, `YOUR_NAME`, `YOUR_EMAIL`
3. **SCM**: Update with your actual repository URLs

```xml
<url>https://github.com/YOUR_USERNAME/gdm-maven-plugin</url>

<developers>
    <developer>
        <id>YOUR_ID</id>
        <name>YOUR_NAME</name>
        <email>YOUR_EMAIL@example.com</email>
    </developer>
</developers>

<scm>
    <connection>scm:git:git://github.com/YOUR_USERNAME/gdm-maven-plugin.git</connection>
    <developerConnection>scm:git:ssh://github.com:YOUR_USERNAME/gdm-maven-plugin.git</developerConnection>
    <url>https://github.com/YOUR_USERNAME/gdm-maven-plugin/tree/main</url>
</scm>
```

## Publishing Process

### Deploy a SNAPSHOT Version

For testing your setup:

```bash
mvn clean deploy
```

This deploys to the OSSRH snapshot repository. Snapshots don't require signing.

### Deploy a Release Version

1. **Update version** (remove `-SNAPSHOT`):
   ```bash
   mvn versions:set -DnewVersion=1.0.0
   mvn versions:commit
   ```

2. **Deploy with the release profile**:
   ```bash
   mvn clean deploy -Prelease
   ```

3. **Or use the Maven Release Plugin** (recommended for proper releases):
   ```bash
   mvn release:clean release:prepare release:perform
   ```

### Release to Maven Central

After deploying to OSSRH staging:

1. Log in to [Nexus Repository Manager](https://s01.oss.sonatype.org/)
2. Click "Staging Repositories"
3. Find your repository (named something like `orgexamplegdm-XXXX`)
4. Click "Close" to validate the release requirements
5. If validation passes, click "Release" to publish to Maven Central

**Note:** With `autoReleaseAfterClose` set to `true` in the nexus-staging-maven-plugin, the release step is automatic after successful close.

## Troubleshooting

### Common Issues

1. **GPG signing fails**
   - Ensure GPG is installed and in your PATH
   - Check that your key is available: `gpg --list-secret-keys`
   - On some systems, you may need to set `GPG_TTY`: `export GPG_TTY=$(tty)`

2. **401 Unauthorized**
   - Verify your OSSRH credentials in `settings.xml`
   - Ensure the `<server><id>` matches `ossrh`

3. **Missing artifacts**
   - Maven Central requires: JAR, sources JAR, javadoc JAR, POM, and signatures for all
   - Ensure the `maven-source-plugin` and `maven-javadoc-plugin` are configured

4. **Validation failures during Close**
   - Check that all required POM elements are present (description, url, licenses, scm, developers)
   - Ensure all artifacts are signed

### Useful Commands

```bash
# Verify your setup without deploying
mvn clean verify -Prelease

# Check GPG agent
gpg-connect-agent /bye

# Export public key for verification
gpg --armor --export YOUR_KEY_ID > public-key.asc
```

## Post-Publication

After your first successful release:

1. Artifacts will be synced to Maven Central within ~2 hours
2. They will be searchable on [search.maven.org](https://search.maven.org) within ~4 hours
3. Future releases follow the same process

## Reference

- [OSSRH Guide](https://central.sonatype.org/publish/publish-guide/)
- [Maven GPG Plugin](https://maven.apache.org/plugins/maven-gpg-plugin/)
- [Nexus Staging Plugin](https://github.com/sonatype/nexus-maven-plugins/tree/main/staging/maven-plugin)

