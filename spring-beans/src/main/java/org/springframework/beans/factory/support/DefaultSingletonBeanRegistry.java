/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.beans.factory.support;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 *
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 *
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 *
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

	/** Maximum number of suppressed exceptions to preserve. */
	private static final int SUPPRESSED_EXCEPTIONS_LIMIT = 100;

	// 一级缓存：单例对象的 cache
	/** Cache of singleton objects: bean name to bean instance. */
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	// 三级缓存：单例对象工厂的 cache
	/** Cache of singleton factories: bean name to ObjectFactory. */
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

	// 二级缓存：提前曝光的单例对象的 Cache
	/** Cache of early singleton objects: bean name to bean instance. */
	private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);

	// 已经注册的 bean 的名称集合
	/** Set of registered singletons, containing the bean names in registration order. */
	private final Set<String> registeredSingletons = new LinkedHashSet<>(256);

	/** Names of beans that are currently in creation. */
	/**
	 * 	标记位 可以用这个 set 来判断对象是否在创建中
	 * 	如果 beanName 在这个集合中，就说明这个 bean 对象正在创建中，如果不在就说明 bean 对象已经创建完成了
	 */
	private final Set<String> singletonsCurrentlyInCreation =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/** Names of beans currently excluded from in creation checks. */
	private final Set<String> inCreationCheckExclusions =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/** Collection of suppressed Exceptions, available for associating related causes. */
	@Nullable
	private Set<Exception> suppressedExceptions;

	/** Flag that indicates whether we're currently within destroySingletons. */
	private boolean singletonsCurrentlyInDestruction = false;

	/** Disposable bean instances: bean name to disposable instance. */
	private final Map<String, Object> disposableBeans = new LinkedHashMap<>();

	/** Map between containing bean names: bean name to Set of bean names that the bean contains. */
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);

	/** Map between dependent bean names: bean name to Set of dependent bean names. */
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

	/** Map between depending bean names: bean name to Set of bean names for the bean's dependencies. */
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);


	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		Assert.notNull(beanName, "Bean name must not be null");
		Assert.notNull(singletonObject, "Singleton object must not be null");
		synchronized (this.singletonObjects) {
			Object oldObject = this.singletonObjects.get(beanName);
			if (oldObject != null) {
				throw new IllegalStateException("Could not register object [" + singletonObject +
						"] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
			}
			addSingleton(beanName, singletonObject);
		}
	}

	/**
	 * 单例缓存：一个 bean 对象只能存在一个缓存中
	 * Add the given singleton object to the singleton cache of this factory.
	 * <p>To be called for eager registration of singletons.
	 * @param beanName the name of the bean
	 * @param singletonObject the singleton object
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			// 一级缓存
			this.singletonObjects.put(beanName, singletonObject);

			// 三级缓存
			this.singletonFactories.remove(beanName);

			// 二级缓存
			this.earlySingletonObjects.remove(beanName);

			// 已经注册的 bean 的名称集合
			this.registeredSingletons.add(beanName);

		}
	}

	/**
	 * 将 key,value 放入三级缓存中，三级缓存的 value 是一个 ObjectFactory
	 *
	 * Add the given singleton factory for building the specified singleton
	 * if necessary.
	 * <p>To be called for eager registration of singletons, e.g. to be able to
	 * resolve circular references.
	 * @param beanName the name of the bean
	 * @param singletonFactory the factory for the singleton object
	 */
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		synchronized (this.singletonObjects) {
			// 如果一级缓存中没有该实例，则放入三级缓存中
			if (!this.singletonObjects.containsKey(beanName)) {
				// 放入三级缓存
				this.singletonFactories.put(beanName, singletonFactory);
				// 从二级缓存中移除
				this.earlySingletonObjects.remove(beanName);
				this.registeredSingletons.add(beanName);
			}
		}
	}

	@Override
	@Nullable
	public Object getSingleton(String beanName) {
		return getSingleton(beanName, true);
	}

	/**
	 * 获取单例对象时会触发三级缓存的处理
	 *
 	 * <p>
 	 *     如果要提早暴露 bean，就必须先通过三级进行一些操作，判定是否是一个需要对外暴露的 bean，
 	 *     确定之后再放入二级缓存中，然后删除三级缓存中的对应 bean，
 	 *     所以二级缓存中包含的一定是要提早暴露的 bean，而三级缓存中包含提早暴露的 bean，但是还包含不能暴露的 bean。
 	 * </p>
 	 *
 	 * <p>
 	 *     我们去 getBean 的时候，想要的 bean 都放在缓存中，就不需要每次 getBean 时都会创建新的 bean 实例
 	 *     一级缓存中的内容，是在每次执行创建 bean 三部曲 AbstractAutowireCapableBeanFactory#doCreateBean 时放的
 	 * </p>
	 *
	 * Return the (raw) singleton object registered under the given name.
	 * <p>Checks already instantiated singletons and also allows for an early
	 * reference to a currently created singleton (resolving a circular reference).
	 * @param beanName the name of the bean to look for
	 * @param allowEarlyReference whether early references should be created or not
	 * @return the registered singleton object, or {@code null} if none found
	 */
	@Nullable
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		// 从一级缓存中获取单例对象
		// Quick check for existing instance without full singleton lock
		Object singletonObject = this.singletonObjects.get(beanName);

// ----------------------- 主要是解决循环依赖的问题 ------------------------------
		// isSingletonCurrentlyInCreation
		//    判断当前实例 bean 是否正在创建中，也就是没有初始化完成
		//	  比如 A 的构造器依赖了 B 对象所以得去创建 B 对象，或者在 A 的 populateBean 过程中依赖了 B 对象，得先去创建 B 对象
		//    此时 A 就是处于创建中的状态
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			// 从二级缓存中获取单例 bean
			singletonObject = this.earlySingletonObjects.get(beanName);
			// allowEarlyReference
			// 		是否允许从 singletonFactories 中通过 getObject 拿到对象
			if (singletonObject == null && allowEarlyReference) {
				synchronized (this.singletonObjects) {
					// 从三级缓存中获取单例 bean
					// Consistent creation of early reference within full singleton lock
					singletonObject = this.singletonObjects.get(beanName);
					// 如果三级缓存中的 bean 为空，才进行下面的操作
					if (singletonObject == null) {
						singletonObject = this.earlySingletonObjects.get(beanName);
						if (singletonObject == null) {
							// 从三级缓存中获取单例 bean
							ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
							if (singletonFactory != null) {
								// 通过单例对象获取单例 bean
								singletonObject = singletonFactory.getObject();
								// 从三级缓存移动到了二级缓存
								this.earlySingletonObjects.put(beanName, singletonObject);
								// 删除三级缓存
								this.singletonFactories.remove(beanName);
							}
						}
					}
				}
			}
		}
// ---------------------------------------------------------------------->

		return singletonObject;
	}

	/**
	 * 获取单例对象
	 *
	 * Return the (raw) singleton object registered under the given name,
	 * creating and registering a new one if none registered yet.
	 * @param beanName the name of the bean
	 * @param singletonFactory the ObjectFactory to lazily create the singleton
	 * with, if necessary
	 * @return the registered singleton object
	 */
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "Bean name must not be null");
		synchronized (this.singletonObjects) {
			Object singletonObject = this.singletonObjects.get(beanName);
			if (singletonObject == null) {
				if (this.singletonsCurrentlyInDestruction) {
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
							"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}

				// 创建之前，设置一个创建中的标识
				beforeSingletonCreation(beanName);
				boolean newSingleton = false;
				boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					this.suppressedExceptions = new LinkedHashSet<>();
				}
// ------------------------------- bean 创建流程完成 ---------------------------------------------------------
				try {
					// 调用匿名内部类获取单例对象
					// 该步骤的完成就意味着 bean 的创建流程完成（获取完了 bean）
					// singletonFactory.getObject() 没有实现类，是靠方法参数 ObjectFactory<?> singletonFactory ，传入一个匿名内部类来实现
					singletonObject = singletonFactory.getObject();
					newSingleton = true;
				}
// ----------------------------------------------------------/
				catch (IllegalStateException ex) {
					// Has the singleton object implicitly appeared in the meantime ->
					// if yes, proceed with it since the exception indicates that state.
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						throw ex;
					}
				}
				catch (BeanCreationException ex) {
					if (recordSuppressedExceptions) {
						for (Exception suppressedException : this.suppressedExceptions) {
							ex.addRelatedCause(suppressedException);
						}
					}
					throw ex;
				}
				finally {
					if (recordSuppressedExceptions) {
						this.suppressedExceptions = null;
					}

					// 创建成功之后，删除创建中的标识，如果存在这个标识，就说明这个 bean 正在创建中
					// 为什么要去标识是否在创建中，是为了解决循环依赖的问题
					afterSingletonCreation(beanName);
				}

				// 将创建的单例 bean 放入缓存中（总共三级缓存）
				if (newSingleton) {
					addSingleton(beanName, singletonObject);
				}
			}
			return singletonObject;
		}
	}

	/**
	 * Register an exception that happened to get suppressed during the creation of a
	 * singleton bean instance, e.g. a temporary circular reference resolution problem.
	 * <p>The default implementation preserves any given exception in this registry's
	 * collection of suppressed exceptions, up to a limit of 100 exceptions, adding
	 * them as related causes to an eventual top-level {@link BeanCreationException}.
	 * @param ex the Exception to register
	 * @see BeanCreationException#getRelatedCauses()
	 */
	protected void onSuppressedException(Exception ex) {
		synchronized (this.singletonObjects) {
			if (this.suppressedExceptions != null && this.suppressedExceptions.size() < SUPPRESSED_EXCEPTIONS_LIMIT) {
				this.suppressedExceptions.add(ex);
			}
		}
	}

	/**
	 * Remove the bean with the given name from the singleton cache of this factory,
	 * to be able to clean up eager registration of a singleton if creation failed.
	 * @param beanName the name of the bean
	 * @see #getSingletonMutex()
	 */
	protected void removeSingleton(String beanName) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.remove(beanName);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.remove(beanName);
		}
	}

	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	@Override
	public String[] getSingletonNames() {
		synchronized (this.singletonObjects) {
			return StringUtils.toStringArray(this.registeredSingletons);
		}
	}

	@Override
	public int getSingletonCount() {
		synchronized (this.singletonObjects) {
			return this.registeredSingletons.size();
		}
	}


	public void setCurrentlyInCreation(String beanName, boolean inCreation) {
		Assert.notNull(beanName, "Bean name must not be null");
		if (!inCreation) {
			this.inCreationCheckExclusions.add(beanName);
		}
		else {
			this.inCreationCheckExclusions.remove(beanName);
		}
	}

	public boolean isCurrentlyInCreation(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
	}

	protected boolean isActuallyInCreation(String beanName) {
		return isSingletonCurrentlyInCreation(beanName);
	}

	/**
	 * Return whether the specified singleton bean is currently in creation
	 * (within the entire factory).
	 * @param beanName the name of the bean
	 */
	public boolean isSingletonCurrentlyInCreation(String beanName) {
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}

	/**
	 * Callback before singleton creation.
	 * <p>The default implementation register the singleton as currently in creation.
	 * @param beanName the name of the singleton about to be created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void beforeSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	/**
	 * Callback after singleton creation.
	 * <p>The default implementation marks the singleton as not in creation anymore.
	 * @param beanName the name of the singleton that has been created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void afterSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}


	/**
	 * Add the given bean to the list of disposable beans in this registry.
	 * <p>Disposable beans usually correspond to registered singletons,
	 * matching the bean name but potentially being a different instance
	 * (for example, a DisposableBean adapter for a singleton that does not
	 * naturally implement Spring's DisposableBean interface).
	 * @param beanName the name of the bean
	 * @param bean the bean instance
	 */
	public void registerDisposableBean(String beanName, DisposableBean bean) {
		synchronized (this.disposableBeans) {
			this.disposableBeans.put(beanName, bean);
		}
	}

	/**
	 * Register a containment relationship between two beans,
	 * e.g. between an inner bean and its containing outer bean.
	 * <p>Also registers the containing bean as dependent on the contained bean
	 * in terms of destruction order.
	 * @param containedBeanName the name of the contained (inner) bean
	 * @param containingBeanName the name of the containing (outer) bean
	 * @see #registerDependentBean
	 */
	public void registerContainedBean(String containedBeanName, String containingBeanName) {
		synchronized (this.containedBeanMap) {
			Set<String> containedBeans =
					this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
			if (!containedBeans.add(containedBeanName)) {
				return;
			}
		}
		registerDependentBean(containedBeanName, containingBeanName);
	}

	/**
	 * Register a dependent bean for the given bean,
	 * to be destroyed before the given bean is destroyed.
	 * @param beanName the name of the bean
	 * @param dependentBeanName the name of the dependent bean
	 */
	public void registerDependentBean(String beanName, String dependentBeanName) {
		String canonicalName = canonicalName(beanName);

		synchronized (this.dependentBeanMap) {
			Set<String> dependentBeans =
					this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
			if (!dependentBeans.add(dependentBeanName)) {
				return;
			}
		}

		synchronized (this.dependenciesForBeanMap) {
			Set<String> dependenciesForBean =
					this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
			dependenciesForBean.add(canonicalName);
		}
	}

	/**
	 * Determine whether the specified dependent bean has been registered as
	 * dependent on the given bean or on any of its transitive dependencies.
	 * @param beanName the name of the bean to check
	 * @param dependentBeanName the name of the dependent bean
	 * @since 4.0
	 */
	protected boolean isDependent(String beanName, String dependentBeanName) {
		synchronized (this.dependentBeanMap) {
			return isDependent(beanName, dependentBeanName, null);
		}
	}

	private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
		if (alreadySeen != null && alreadySeen.contains(beanName)) {
			return false;
		}
		String canonicalName = canonicalName(beanName);
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		if (dependentBeans == null) {
			return false;
		}
		if (dependentBeans.contains(dependentBeanName)) {
			return true;
		}
		for (String transitiveDependency : dependentBeans) {
			if (alreadySeen == null) {
				alreadySeen = new HashSet<>();
			}
			alreadySeen.add(beanName);
			if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine whether a dependent bean has been registered for the given name.
	 * @param beanName the name of the bean to check
	 */
	protected boolean hasDependentBean(String beanName) {
		return this.dependentBeanMap.containsKey(beanName);
	}

	/**
	 * Return the names of all beans which depend on the specified bean, if any.
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 */
	public String[] getDependentBeans(String beanName) {
		Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
		if (dependentBeans == null) {
			return new String[0];
		}
		synchronized (this.dependentBeanMap) {
			return StringUtils.toStringArray(dependentBeans);
		}
	}

	/**
	 * Return the names of all beans that the specified bean depends on, if any.
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 */
	public String[] getDependenciesForBean(String beanName) {
		Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
		if (dependenciesForBean == null) {
			return new String[0];
		}
		synchronized (this.dependenciesForBeanMap) {
			return StringUtils.toStringArray(dependenciesForBean);
		}
	}

	public void destroySingletons() {
		if (logger.isTraceEnabled()) {
			logger.trace("Destroying singletons in " + this);
		}
		synchronized (this.singletonObjects) {
			this.singletonsCurrentlyInDestruction = true;
		}

		String[] disposableBeanNames;
		synchronized (this.disposableBeans) {
			disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
		}
		for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
			destroySingleton(disposableBeanNames[i]);
		}

		this.containedBeanMap.clear();
		this.dependentBeanMap.clear();
		this.dependenciesForBeanMap.clear();

		clearSingletonCache();
	}

	/**
	 * Clear all cached singleton instances in this registry.
	 * @since 4.3.15
	 */
	protected void clearSingletonCache() {
		synchronized (this.singletonObjects) {
			this.singletonObjects.clear();
			this.singletonFactories.clear();
			this.earlySingletonObjects.clear();
			this.registeredSingletons.clear();
			this.singletonsCurrentlyInDestruction = false;
		}
	}

	/**
	 * Destroy the given bean. Delegates to {@code destroyBean}
	 * if a corresponding disposable bean instance is found.
	 * @param beanName the name of the bean
	 * @see #destroyBean
	 */
	public void destroySingleton(String beanName) {
		// Remove a registered singleton of the given name, if any.
		removeSingleton(beanName);

		// Destroy the corresponding DisposableBean instance.
		DisposableBean disposableBean;
		synchronized (this.disposableBeans) {
			disposableBean = (DisposableBean) this.disposableBeans.remove(beanName);
		}
		destroyBean(beanName, disposableBean);
	}

	/**
	 * Destroy the given bean. Must destroy beans that depend on the given
	 * bean before the bean itself. Should not throw any exceptions.
	 * @param beanName the name of the bean
	 * @param bean the bean instance to destroy
	 */
	protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
		// Trigger destruction of dependent beans first...
		Set<String> dependencies;
		synchronized (this.dependentBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			dependencies = this.dependentBeanMap.remove(beanName);
		}
		if (dependencies != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Retrieved dependent beans for bean '" + beanName + "': " + dependencies);
			}
			for (String dependentBeanName : dependencies) {
				destroySingleton(dependentBeanName);
			}
		}

		// Actually destroy the bean now...
		if (bean != null) {
			try {
				bean.destroy();
			}
			catch (Throwable ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Destruction of bean with name '" + beanName + "' threw an exception", ex);
				}
			}
		}

		// Trigger destruction of contained beans...
		Set<String> containedBeans;
		synchronized (this.containedBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			containedBeans = this.containedBeanMap.remove(beanName);
		}
		if (containedBeans != null) {
			for (String containedBeanName : containedBeans) {
				destroySingleton(containedBeanName);
			}
		}

		// Remove destroyed bean from other beans' dependencies.
		synchronized (this.dependentBeanMap) {
			for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, Set<String>> entry = it.next();
				Set<String> dependenciesToClean = entry.getValue();
				dependenciesToClean.remove(beanName);
				if (dependenciesToClean.isEmpty()) {
					it.remove();
				}
			}
		}

		// Remove destroyed bean's prepared dependency information.
		this.dependenciesForBeanMap.remove(beanName);
	}

	/**
	 * Exposes the singleton mutex to subclasses and external collaborators.
	 * <p>Subclasses should synchronize on the given Object if they perform
	 * any sort of extended singleton creation phase. In particular, subclasses
	 * should <i>not</i> have their own mutexes involved in singleton creation,
	 * to avoid the potential for deadlocks in lazy-init situations.
	 */
	@Override
	public final Object getSingletonMutex() {
		return this.singletonObjects;
	}

}
