/*
 * Copyright 2015-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jp.xet.sparwings.aws.sqs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.function.Consumer;

import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.DigestUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

/**
 * Test for {@link SqsMessagePoller}.
 * 
 * @since 0.10
 * @version $Id$
 * @author daisuke
 */
@SuppressWarnings("javadoc")
@RunWith(MockitoJUnitRunner.class)
public class SqsMessagePollerTest {
	
	private static Logger logger = LoggerFactory.getLogger(SqsMessagePollerTest.class);
	
	private static final String Q_URL = "http://example.com";
	
	@Mock
	SqsClient sqs;
	
	@Spy
	RetryTemplate retry = new RetryTemplate(); // retry 3 times
	
	@Mock
	Consumer<Message> messageHandler;
	
	SqsMessagePoller sut;
	
	
	@Before
	public void setUp() throws Exception {
		sut = new SqsMessagePoller(sqs, retry, Q_URL, messageHandler);
		sut.setVisibilityTimeout(10);
		sut.setChangeVisibilityThreshold(1);
	}
	
	private ReceiveMessageResponse receiveMessageResultOf(Message... msgs) {
		return ReceiveMessageResponse.builder().messages(msgs).build();
	}
	
	private Message createMessage(int i) {
		String body = "body-" + i;
		return Message.builder()
			.messageId("mid-" + i)
			.body(body)
			.receiptHandle("rh-" + i)
			.md5OfBody(DigestUtils.md5DigestAsHex(body.getBytes()))
			.build();
	}
	
	private DeleteMessageRequest createDeleteMessageRequest(int i) {
		return DeleteMessageRequest.builder()
			.queueUrl(Q_URL)
			.receiptHandle("rh-" + i)
			.build();
	}
	
	private Answer<?> createHeavyJobAnswer(int size, boolean excepiton) {
		return invocation -> {
			for (int i = 0; i < size; i++) {
				logger.info("handler is working... {}/{}", i + 1, size);
				Thread.sleep(333);
			}
			if (excepiton) {
				throw new Exception(); // NOPMD
			}
			return null;
		};
	}
	
	@Test
	public void test_0Messages() throws Exception {
		// setup
		when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
			.thenReturn(receiveMessageResultOf());
		// exercise
		sut.loop();
		// verify
		verify(sqs).receiveMessage(any(ReceiveMessageRequest.class));
		verify(messageHandler, never()).accept(any(Message.class));
		verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
		verify(sqs, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
	}
	
	@Test
	public void test_1Message() throws Exception {
		// setup
		Message msg1 = createMessage(1);
		DeleteMessageRequest expectedDmr = createDeleteMessageRequest(1);
		when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultOf(msg1));
		// exercise
		sut.loop();
		// verify
		verify(sqs).receiveMessage(any(ReceiveMessageRequest.class));
		verify(messageHandler).accept(eq(msg1));
		verify(sqs).deleteMessage(eq(expectedDmr));
		verify(sqs, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
	}
	
	@Test
	public void test_3Message() throws Exception {
		// setup
		Message msg1 = createMessage(1);
		Message msg2 = createMessage(2);
		Message msg3 = createMessage(3);
		DeleteMessageRequest expectedDmr1 = createDeleteMessageRequest(1);
		DeleteMessageRequest expectedDmr2 = createDeleteMessageRequest(2);
		DeleteMessageRequest expectedDmr3 = createDeleteMessageRequest(3);
		when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
			.thenReturn(receiveMessageResultOf(msg1, msg2, msg3));
		// exercise
		sut.loop();
		// verify
		verify(sqs).receiveMessage(any(ReceiveMessageRequest.class));
		verify(messageHandler).accept(eq(msg1));
		verify(messageHandler).accept(eq(msg2));
		verify(messageHandler).accept(eq(msg3));
		verify(sqs).deleteMessage(eq(expectedDmr1));
		verify(sqs).deleteMessage(eq(expectedDmr2));
		verify(sqs).deleteMessage(eq(expectedDmr3));
		verify(sqs, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
	}
	
	@Test
	public void test_1HeavyMessage() throws Exception {
		// setup
		Message msg1 = createMessage(1);
		DeleteMessageRequest expectedDmr = createDeleteMessageRequest(1);
		when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultOf(msg1));
		doAnswer(createHeavyJobAnswer(4, false)).when(messageHandler).accept(any(Message.class));
		// exercise
		sut.loop();
		// verify
		verify(sqs).receiveMessage(any(ReceiveMessageRequest.class));
		verify(messageHandler).accept(eq(msg1));
		verify(sqs).deleteMessage(eq(expectedDmr));
		
		ArgumentCaptor<ChangeMessageVisibilityRequest> captor =
				ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
		verify(sqs, times(1)).changeMessageVisibility(captor.capture());
		ChangeMessageVisibilityRequest cmvReq = captor.getValue();
		assertThat(cmvReq.queueUrl(), is(Q_URL));
		assertThat(cmvReq.receiptHandle(), is("rh-1"));
		assertThat(cmvReq.visibilityTimeout(), is(10));
	}
	
	@Test
	public void test_3HeavyMessage() throws Exception {
		// setup
		Message msg1 = createMessage(1);
		Message msg2 = createMessage(2);
		Message msg3 = createMessage(3);
		DeleteMessageRequest expectedDmr1 = createDeleteMessageRequest(1);
		DeleteMessageRequest expectedDmr2 = createDeleteMessageRequest(2);
		DeleteMessageRequest expectedDmr3 = createDeleteMessageRequest(3);
		doReturn(receiveMessageResultOf(msg1, msg2, msg3))
			.when(sqs).receiveMessage(any(ReceiveMessageRequest.class));
		doAnswer(createHeavyJobAnswer(4, false))
			.when(messageHandler).accept(any(Message.class));
		// exercise
		sut.loop();
		// verify
		verify(sqs).receiveMessage(any(ReceiveMessageRequest.class));
		verify(messageHandler).accept(eq(msg1));
		verify(messageHandler).accept(eq(msg2));
		verify(messageHandler).accept(eq(msg3));
		verify(sqs).deleteMessage(eq(expectedDmr1));
		verify(sqs).deleteMessage(eq(expectedDmr2));
		verify(sqs).deleteMessage(eq(expectedDmr3));
		ArgumentCaptor<ChangeMessageVisibilityRequest> captor =
				ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
		verify(sqs, times(3)).changeMessageVisibility(captor.capture());
		
		ChangeMessageVisibilityRequest cmvReq1 = captor.getAllValues().stream()
			.filter(r -> r.receiptHandle().equals("rh-1")).findFirst().get();
		assertThat(cmvReq1.queueUrl(), is(Q_URL));
		assertThat(cmvReq1.visibilityTimeout(), is(10));
		
		ChangeMessageVisibilityRequest cmvReq2 = captor.getAllValues().stream()
			.filter(r -> r.receiptHandle().equals("rh-2")).findFirst().get();
		assertThat(cmvReq2.queueUrl(), is(Q_URL));
		assertThat(cmvReq2.receiptHandle(), is("rh-2"));
		assertThat(cmvReq2.visibilityTimeout(), is(10));
		
		ChangeMessageVisibilityRequest cmvReq3 = captor.getAllValues().stream()
			.filter(r -> r.receiptHandle().equals("rh-3")).findFirst().get();
		assertThat(cmvReq3.queueUrl(), is(Q_URL));
		assertThat(cmvReq3.receiptHandle(), is("rh-3"));
		assertThat(cmvReq3.visibilityTimeout(), is(10));
	}
	
	@Test
	public void test_1VeryHeavyMessage() throws Exception {
		// setup
		Message msg1 = createMessage(1);
		DeleteMessageRequest expectedDmr = createDeleteMessageRequest(1);
		when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultOf(msg1));
		doAnswer(createHeavyJobAnswer(7, false)).when(messageHandler).accept(any(Message.class));
		// exercise
		sut.loop();
		// verify
		verify(sqs).receiveMessage(any(ReceiveMessageRequest.class));
		verify(messageHandler).accept(eq(msg1));
		verify(sqs).deleteMessage(eq(expectedDmr));
		
		ArgumentCaptor<ChangeMessageVisibilityRequest> captor =
				ArgumentCaptor.forClass(ChangeMessageVisibilityRequest.class);
		verify(sqs, times(2)).changeMessageVisibility(captor.capture());
		
		ChangeMessageVisibilityRequest cmvReq1 = captor.getAllValues().get(0);
		assertThat(cmvReq1.queueUrl(), is(Q_URL));
		assertThat(cmvReq1.receiptHandle(), is("rh-1"));
		assertThat(cmvReq1.visibilityTimeout(), is(10));
		
		ChangeMessageVisibilityRequest cmvReq2 = captor.getAllValues().get(1);
		assertThat(cmvReq2.queueUrl(), is(Q_URL));
		assertThat(cmvReq2.receiptHandle(), is("rh-1"));
		assertThat(cmvReq2.visibilityTimeout(), is(10));
	}
	
	@Test
	public void test_1ExcessiveHeavyMessage() throws Exception {
		// setup
		sut.setVisibilityTimeout(60);
		sut.setChangeVisibilityThreshold(1);
		Message msg1 = createMessage(1);
		DeleteMessageRequest expectedDmr = createDeleteMessageRequest(1);
		when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultOf(msg1));
		doAnswer(createHeavyJobAnswer(24, false)).when(messageHandler).accept(any(Message.class));
		// exercise
		sut.loop();
		// verify
		verify(sqs).receiveMessage(any(ReceiveMessageRequest.class));
		verify(messageHandler).accept(eq(msg1));
		verify(sqs, never()).deleteMessage(eq(expectedDmr)); // retry attempt exceeded
		verify(sqs, times(3)).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
	}
	
	@Test
	public void test_1MessageWithFailureHandler() throws Exception {
		// setup
		Message msg1 = createMessage(1);
		DeleteMessageRequest expectedDmr = createDeleteMessageRequest(1);
		when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultOf(msg1));
		doThrow(RuntimeException.class).when(messageHandler).accept(any(Message.class));
		// exercise
		sut.loop();
		// verify
		verify(sqs).receiveMessage(any(ReceiveMessageRequest.class));
		verify(messageHandler).accept(eq(msg1));
		verify(sqs, never()).deleteMessage(eq(expectedDmr));
		verify(sqs, never()).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
	}
	
	@Test
	public void test_1HeavyMessageWithFailureHandler() throws Exception {
		// setup
		Message msg1 = createMessage(1);
		DeleteMessageRequest expectedDmr = createDeleteMessageRequest(1);
		when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveMessageResultOf(msg1));
		doAnswer(createHeavyJobAnswer(4, true)).when(messageHandler).accept(any(Message.class));
		// exercise
		sut.loop();
		// verify
		verify(sqs).receiveMessage(any(ReceiveMessageRequest.class));
		verify(messageHandler).accept(eq(msg1));
		verify(sqs, never()).deleteMessage(eq(expectedDmr));
		verify(sqs).changeMessageVisibility(any(ChangeMessageVisibilityRequest.class));
	}
}
