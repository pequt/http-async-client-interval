package com.example.demo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {
	private final int port;

	public DemoController(@Value("${server.port}") int port) {
		this.port = port;
	}

	@GetMapping("/delay/{time}")
	public int delay(@PathVariable int time) {
		sleep(time);
		return time;
	}

	@GetMapping("/test/{time}")
	public String test(@PathVariable int time,
		@RequestParam int timeout,
		@RequestParam(required = false) Integer interval) throws IOException, ExecutionException, InterruptedException {

		RequestConfig requestConfig = RequestConfig.custom()
			.setConnectTimeout(timeout)
			.setSocketTimeout(timeout)
			.setConnectionRequestTimeout(timeout)
			.build();

		HttpAsyncClientBuilder clb = HttpAsyncClients.custom()
			.setDefaultRequestConfig(requestConfig);

		if (interval != null) {
			// https://stackoverflow.com/questions/38178473/unable-to-set-socket-timeout-less-than-1000-milliseconds-in-requestconfig-apach

			// Timeout of a request will be checked as set in RequestConfig
			// but the 'timeout response' will be returned in every set interval scheduled, not the instant timeout happened.

			// ex) interval = 1000ms, timeout happened = 1100ms > then you can have the timeout response in 2000ms
			// Default interval is 1000ms, and too short interval makes cpu unnecessarily busy.
			IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
				.setSelectInterval(interval)
				.setConnectTimeout(timeout)
				.setSoTimeout(timeout)
				.build();
			clb.setDefaultIOReactorConfig(ioReactorConfig);
		}

		CloseableHttpAsyncClient cl = clb.build();
		cl.start();

		String url = "http://localhost:" + port + "/delay/" + time;
		HttpGet req = new HttpGet(url);
		Future<HttpResponse> futureResp = cl.execute(req, null);
		HttpResponse resp = futureResp.get();
		cl.close();

		return inputStreamToString(resp.getEntity().getContent());
	}

	private String inputStreamToString(InputStream inputStream) throws IOException {
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int length;
		while ((length = inputStream.read(buffer)) != -1) {
			result.write(buffer, 0, length);
		}
		return result.toString(StandardCharsets.UTF_8.name());
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			// IGNORE
		}
	}
}
