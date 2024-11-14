import com.google.mlkit.vision.demo.kotlin.LogApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.HostnameVerifier

object RetrofitClient {
    private const val BASE_URL = "https://10.0.2.2:8443/" // https 프로토콜로 변경

    // SSL 검증을 우회하는 클라이언트 설정
    private val client = OkHttpClient.Builder().apply {
        // SSLContext를 설정하여 모든 인증서를 신뢰하도록 구성
        val trustAllCertificates = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        }

        // SSLContext 초기화
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustAllCertificates), java.security.SecureRandom())

        // SSLContext를 OkHttpClient에 적용
        this.sslSocketFactory(sslContext.socketFactory, trustAllCertificates)

        // 모든 호스트 이름을 허용하는 HostnameVerifier 설정
        this.hostnameVerifier(HostnameVerifier { _, _ -> true })


        // 로깅 설정
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
        this.addInterceptor(loggingInterceptor)

    }.build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)  // custom client 적용
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val logApiService: LogApiService = retrofit.create(LogApiService::class.java)
}
