/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.servlet.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.context.request.AsyncWebRequestInterceptor;
import org.springframework.web.context.request.WebRequestInterceptor;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Adapter that implements the Servlet HandlerInterceptor interface
 * and wraps an underlying WebRequestInterceptor.
 *
 * @ref [SpringBoot之HandlerInterceptorAdapter] https://www.cnblogs.com/weianlai/p/11358768.html
 *
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see org.springframework.web.context.request.WebRequestInterceptor
 * @see org.springframework.web.servlet.HandlerInterceptor
 */
public class WebRequestHandlerInterceptorAdapter implements AsyncHandlerInterceptor {

	private final WebRequestInterceptor requestInterceptor;


	/**
	 * Create a new WebRequestHandlerInterceptorAdapter for the given WebRequestInterceptor.
	 * @param requestInterceptor the WebRequestInterceptor to wrap
	 */
	public WebRequestHandlerInterceptorAdapter(WebRequestInterceptor requestInterceptor) {
		Assert.notNull(requestInterceptor, "WebRequestInterceptor must not be null");
		this.requestInterceptor = requestInterceptor;
	}


	/**
	 * 预处理回调方法
	 * <p> 实现处理器的预处理（如检查登录），第三个参数为响应的处理器，自定义 Controller </p>
	 *
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler chosen handler to execute, for type and/or instance evaluation
	 * @return boolean  [
	 * 					true 继续流程（如调用下一个拦截器或处理器） |
	 * 					false 流程中断（如登录检查失败），不会继续调用其他的拦截器或处理器，此时我们需要通过 response 来产生响应
	 * 					]
	 * @throws Exception
	 */
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {

		this.requestInterceptor.preHandle(new DispatcherServletWebRequest(request, response));
		return true;
	}

	/**
	 * 后处理回调方法
	 * <p> 实现处理器的后处理（但在渲染视图之前）</p>
	 * <p>此时我们可以通过 modelAndView（模型和视图对象），modelAndView 也可能为 null</p>
	 *
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the handler (or {@link HandlerMethod}) that started asynchronous
	 * execution, for type and/or instance examination
	 * @param modelAndView the {@code ModelAndView} that the handler returned
	 * (can also be {@code null})
	 * @throws Exception
	 */
	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			@Nullable ModelAndView modelAndView) throws Exception {

		this.requestInterceptor.postHandle(new DispatcherServletWebRequest(request, response),
				(modelAndView != null && !modelAndView.wasCleared() ? modelAndView.getModelMap() : null));
	}

	/**
	 * 整个请求处理完毕回调方法
	 *
	 * <p>即在视图渲染完毕时回调，即在视图渲染完毕时回调，如性能监控中我们可以在此记录结束时间并输出消耗时间</p>
	 * <p>还可以进行一些资源清理，类似于 try-catch-finally 中的 finally ，但仅调用处理器执行链中的</p>
	 *
	 * @param request current HTTP request
	 * @param response current HTTP response
	 * @param handler the handler (or {@link HandlerMethod}) that started asynchronous
	 * execution, for type and/or instance examination
	 * @param ex any exception thrown on handler execution, if any; this does not
	 * include exceptions that have been handled through an exception resolver
	 * @throws Exception
	 */
	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			@Nullable Exception ex) throws Exception {

		this.requestInterceptor.afterCompletion(new DispatcherServletWebRequest(request, response), ex);
	}

	@Override
	public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (this.requestInterceptor instanceof AsyncWebRequestInterceptor asyncInterceptor) {
			DispatcherServletWebRequest webRequest = new DispatcherServletWebRequest(request, response);
			asyncInterceptor.afterConcurrentHandlingStarted(webRequest);
		}
	}

}
