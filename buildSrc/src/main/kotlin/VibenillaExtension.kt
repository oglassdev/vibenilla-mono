import org.gradle.api.provider.Property

enum class License(
    val id: String,
    val url: String,
) {
    APACHE_2("Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0.txt"),
    MIT("MIT", "https://opensource.org/license/mit"),
}

interface VibenillaExtension {
    val license: Property<License>
}
