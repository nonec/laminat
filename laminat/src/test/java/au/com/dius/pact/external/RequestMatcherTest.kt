package au.com.dius.pact.external

import au.com.dius.pact.consumer.ConsumerPactBuilder
import au.com.dius.pact.consumer.dsl.PactDslJsonBody
import au.com.dius.pact.model.RequestResponseInteraction
import okhttp3.Headers
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import java.io.File
import java.net.InetAddress
import java.net.Socket

/**
 * This test checks that the RequestMatcher matches incoming requests correctly.
 *
 * @see RequestMatcher
 * @author Jan Phillip Kretzschmar
 */
class RequestMatcherTest {

    private val unusedPOST by lazy {
        ConsumerPactBuilder("TestConsumer").hasPactWith("TestProducer")
            .uponReceiving("POST unusedRequest")
            .method("POST")
            .path("/unused/path")
            .headers(
                hashMapOf(
                    Pair("Content-Type", "application/json")
                )
            )
            .body(
                PactDslJsonBody()
                    .stringMatcher("regex1", "\\d{8,9}", "123456789")
                    .stringMatcher("regex2", ".{4}", "abcd")
                    .decimalType("decimal1", 50.99234)
            )
            .willRespondWith()
            .status(200)
            .headers(hashMapOf(Pair("Content-Type", "application/json; charset=UTF-8")))
            .body(
                PactDslJsonBody()
                    .stringMatcher("regex3", "\\d{5,6}", "12345")
                    .stringMatcher("regex4", ".{3}", "abc")
            )
            .toPact()
    }

    private val testPost by lazy {
        ConsumerPactBuilder("TestConsumer").hasPactWith("TestProducer")
            .uponReceiving("POST testRequest")
            .method("POST")
            .path("/test/path")
            .headers(
                hashMapOf(
                    Pair("Content-Type", "application/json")
                )
            )
            .body(
                PactDslJsonBody()
                    .stringMatcher("regex1", "\\d{8,9}", "123456789")
                    .stringMatcher("regex2", ".{4}", "abcd")
                    .decimalType("decimal1", 50.99234)
            )
            .willRespondWith()
            .status(200)
            .headers(hashMapOf(Pair("Content-Type", "application/json; charset=UTF-8")))
            .body(
                PactDslJsonBody()
                    .stringMatcher("regex3", "\\d{5,6}", "12345")
                    .stringMatcher("regex4", ".{3}", "abc")
            )
            .toPact()
    }

    private val testPostArray by lazy {
        ConsumerPactBuilder("TestConsumer").hasPactWith("TestProducer")
            .uponReceiving("POST testRequest")
            .method("POST")
            .path("/test/path")
            .headers(
                hashMapOf(
                    Pair("Content-Type", "application/json")
                )
            )
            .body(
                PactDslJsonBody()
                    .array("array")
                    .`object`()
                    .stringMatcher("regex1", "\\d{8,9}", "123456789")
                    .stringMatcher("regex2", ".{4}", "abcd")
                    .decimalType("decimal1", 50.99234)
                    .closeObject()
                    !!.`object`()
                    .stringMatcher("regex1", "\\d{8,9}", "123456789")
                    .stringMatcher("regex2", ".{4}", "abcd")
                    .decimalType("decimal1", 50.99234)
                    .closeObject()
                    !!.`object`()
                    .minArrayLike("nestedArray", 2)
                    .stringMatcher("regex5", "\\d{9}")
                    .closeObject()
                    !!.closeArray()
                    .closeObject()
                    !!.closeArray()

            )
            .willRespondWith()
            .status(200)
            .headers(hashMapOf(Pair("Content-Type", "application/json; charset=UTF-8")))
            .body(
                PactDslJsonBody()
                    .`object`("_embedded")
                        .stringMatcher("regex3", "\\d{5,6}", "12345")
                        .stringMatcher("regex4", ".{3}", "abc")
                        .array("firstArray")
                            .`object`().closeObject()
                            !!.`object`().closeObject()
                            !!.`object`().closeObject()
                            !!.`object`()
                                .minArrayLike("nestedArray", 80)
                                .stringMatcher("uid", "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|([A-Z0-9]{40})")
                                .closeObject()
                                !!.closeArray()
                            .closeObject()
                        !!.closeArray()
                    .closeObject()
            )
            .toPact()
    }

    private val hugeAuthorization = String(CharArray(200000) { 'C' })

    private val testGet by lazy {
        ConsumerPactBuilder("TestConsumer").hasPactWith("TestProducer")
            .uponReceiving("GET huge authorization header")
            .method("GET")
            .path("/test/path")
            .headers(
                hashMapOf(
                    Pair("Authorization", hugeAuthorization)
                )
            )
            .willRespondWith()
            .status(200)
            .headers(hashMapOf(Pair("Content-Type", "application/json; charset=UTF-8")))
            .body(
                PactDslJsonBody()
                    .stringMatcher("regex3", "\\d{5,6}", "12345")
                    .stringMatcher("regex4", ".{3}", "abc")
            )
            .toPact()
    }

    private fun getMockSocket(): Socket {
        val mockInetAddress = mock(InetAddress::class.java)
        doReturn("mockhost").`when`(mockInetAddress).hostName

        val mockSocket = mock(Socket::class.java)
        doReturn(mockInetAddress).`when`(mockSocket).inetAddress
        doReturn(1234).`when`(mockSocket).localPort

        return mockSocket
    }

    private fun getIncomingRequest(requestBody: ByteArray, method: String = "POST", authorization: String = ""): IncomingRequest {
        val mockSocket = getMockSocket()
        val headers = Headers.Builder()
            .add("Authorization: $authorization")
            .add("Content-Type: application/json")
            .add("Content-Length: ${requestBody.size}")
            .add("Host: localhost:41163")
            .add("Connection: Keep-Alive")
            .add("Accept-Encoding: gzip")
            .add("User-Agent: okhttp/3.9.0")
            .add("Accept-Language: de")
            .build()
        val body = Buffer()
        body.outputStream().use {
            it.write(requestBody)
        }
        val recordedRequest = RecordedRequest("$method /test/path HTTP/1.1", headers, ArrayList(), body.size(), body, 0, mockSocket)
        return IncomingRequest(recordedRequest)
    }

    @Test
    fun pactDispatcher_PostRequest_MatchingCorrectly() {
        val request = "{ \"regex1\": \"123456789\", \"regex2\": \"abcd\", \"decimal1\": 50.99234}".toByteArray()

        val matcher = RequestMatcher(false)
        val incomingRequest = getIncomingRequest(request)

        val interactions = testPost.interactions.map { it as RequestResponseInteraction }

        when (val match = matcher.findInteraction(interactions, incomingRequest)) {
            is RequestMatch.FullRequestMatch -> return
            is RequestMatch.PartialRequestMatch -> {
                Assert.fail("Match is only a Partial Request Match: \n${match.problems.joinToString("\n")}")
            }
            is RequestMatch.RequestMismatch -> {
                Assert.fail("Match is only a Request Mismatch: \n${match.problems?.joinToString("\n")}")
            }
        }
    }

    @Test
    fun pactDispatcher_PostRequestMultipleInteractions_MatchingCorrectly() {
        val request = "{ \"regex1\": \"123456789\", \"regex2\": \"abcd\", \"decimal1\": 50.99234}".toByteArray()

        val matcher = RequestMatcher(false)
        val recordedRequest = getIncomingRequest(request)

        val interactions = testPost.interactions.plus(unusedPOST.interactions).map { it as RequestResponseInteraction }

        when (val match = matcher.findInteraction(interactions, recordedRequest)) {
            is RequestMatch.FullRequestMatch -> return
            is RequestMatch.PartialRequestMatch -> {
                Assert.fail("Match is only a Partial Request Match: \n${match.problems.joinToString("\n")}")
            }
            is RequestMatch.RequestMismatch -> {
                Assert.fail("Match is only a Request Mismatch: \n${match.problems?.joinToString("\n")}")
            }
        }
    }

    @Test
    fun pactDispatcher_PostRequest_NotMatching() {
        val request = "{ \"regex1\": \"1\", \"decimal1\": 50.999234, \"unexpected\":\"skdfskjdf\"}".toByteArray()

        val matcher = RequestMatcher(false)
        val recordedRequest = getIncomingRequest(request)

        val interactions = testPost.interactions.map { it as RequestResponseInteraction }

        when (val match = matcher.findInteraction(interactions, recordedRequest)) {
            is RequestMatch.FullRequestMatch -> {
                Assert.fail("Match was a Full Request Match!")
            }
            is RequestMatch.PartialRequestMatch -> {
                Assert.assertEquals("MismatchedBody on null:\nExpected '1' to match '\\d{8,9}'", match.problems[0].message)
                Assert.assertEquals("MismatchedBody on \$:\nExpected regex2=\"abcd\" but was missing", match.problems[1].message)
            }
            is RequestMatch.RequestMismatch -> {
                Assert.fail("Match is only a Request Mismatch: \n${match.problems?.joinToString("\n")}")
            }
        }
    }

    @Test
    fun pactDispatcher_PostArrayRequest_MatchingCorrectly() {
        val arrayObject = "{ \"regex1\": \"123456789\", \"regex2\": \"abcd\", \"decimal1\": 50.99234}"
        val nestedArrayObject = "{ \"nestedArray\": [ { \"regex5\": \"123456789\" }, { \"regex5\": \"987654321\" } ] }"
        val request = "{ \"array\": [$arrayObject,$arrayObject,$nestedArrayObject]}".toByteArray()

        val matcher = RequestMatcher(false)
        val recordedRequest = getIncomingRequest(request)

        val interactions = testPostArray.interactions.map { it as RequestResponseInteraction }

        when (val match = matcher.findInteraction(interactions, recordedRequest)) {
            is RequestMatch.FullRequestMatch -> {
                val response = match.interaction.response.generateResponse()
                Assert.assertNotNull(response)
            }
            is RequestMatch.PartialRequestMatch -> {
                Assert.fail("Match is only a Partial Request Match: \n${match.problems.joinToString("\n")}")
            }
            is RequestMatch.RequestMismatch -> {
                Assert.fail("Match is only a Request Mismatch: \n${match.problems?.joinToString("\n")}")
            }
        }
    }

    @Test
    fun pactDispatcher_LongGetRequest_MatchingCorrectly() {

        val matcher = RequestMatcher(false)

        val recordedRequest = getIncomingRequest(ByteArray(0), "GET", hugeAuthorization)

        val interactions = testGet.interactions.map { it as RequestResponseInteraction }

        when (val match = matcher.findInteraction(interactions, recordedRequest)) {
            is RequestMatch.FullRequestMatch -> return
            is RequestMatch.PartialRequestMatch -> {
                Assert.fail("Match is only a Partial Request Match: \n${match.problems.joinToString("\n")}")
            }
            is RequestMatch.RequestMismatch -> {
                Assert.fail("Match is only a Request Mismatch: \n${match.problems?.joinToString("\n")}")
            }
        }
    }

    @Test
    fun pactDispatcher_Serialize_Pact() {
        PactJsonifier.generateJson(listOf(testPostArray), File("build/outputs/pact"))
    }
}