package tech.kelma.app

/**
 * KelmaSync server addresses chosen at sign-in.
 *
 * Every saved account belongs to exactly one server (its registry key is the
 * server plus the username), so one account can sync with Kelma while another
 * uses a self-hosted KelmaSync server. Accounts on a self-hosted server are
 * created by whoever runs it; Review only signs in.
 */
internal object SyncServerAddress {
    private val addressPattern = Regex("^(https?)://([^/?#@\\s]+)(/[^?#\\s]*)?$", RegexOption.IGNORE_CASE)
    private val hostPattern = Regex("^(\\[[0-9a-fA-F:.]+]|[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?)(:(\\d{1,5}))?$")

    /** A canonical server URL, or [IllegalArgumentException] with a message for the user. */
    fun normalize(input: String): String {
        var value = input.trim()
        require(value.isNotEmpty()) { "Enter the server's address, for example https://sync.example.org." }
        if (!value.contains("://")) value = "https://$value"
        val match = addressPattern.matchEntire(value)
            ?: throw IllegalArgumentException(
                "Enter only the server's address, starting with https:// (or http:// on your own network).",
            )
        val scheme = match.groupValues[1].lowercase()
        val authority = hostPattern.matchEntire(match.groupValues[2])
            ?: throw IllegalArgumentException("That server name is not valid.")
        val host = authority.groupValues[1].lowercase()
        val port = authority.groupValues[4].takeIf(String::isNotEmpty)?.toInt()
        require(port == null || port in 1..65_535) { "The port number is not valid." }
        val defaultPort = if (scheme == "https") 443 else 80
        val portSuffix = if (port == null || port == defaultPort) "" else ":$port"
        val path = match.groupValues[3].trimEnd('/')
        val url = "$scheme://$host$portSuffix$path"
        return if (url in LegacyKelmaSyncEndpoints) DefaultKelmaSyncEndpoint else url
    }

    fun isDefault(endpoint: String): Boolean =
        runCatching { normalize(endpoint) == DefaultKelmaSyncEndpoint }.getOrDefault(false)

    /** The server's host (with a non-default port), e.g. `sync.example.org:8443`. */
    fun host(endpoint: String): String = endpoint.substringAfter("://").substringBefore('/')

    /** "Kelma" for Kelma's server, otherwise its host. */
    fun label(endpoint: String): String = if (isDefault(endpoint)) "Kelma" else host(endpoint)

    /** Plain http:// to a server outside the local network. */
    fun isUnencryptedRemote(endpoint: String): Boolean {
        if (!endpoint.startsWith("http://", ignoreCase = true)) return false
        val name = host(endpoint).substringBeforeLast(':').removePrefix("[").removeSuffix("]").lowercase()
        val local = name == "localhost" || name.endsWith(".local") || name == "::1" ||
            name.startsWith("127.") || name.startsWith("10.") || name.startsWith("192.168.") ||
            Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(name)
        return !local
    }
}

private val LegacyKelmaSyncEndpoints = setOf("https://sync2.ankiai.tech")
