package jp.co.qoncept.promise;

import java.util.ArrayList;
import java.util.List;

import jp.co.qoncept.functional.Consumer;
import jp.co.qoncept.functional.Function;
import jp.co.qoncept.functional.Supplier;
import jp.co.qoncept.util.Tuple3;
import jp.co.qoncept.util.Tuple4;

public class Promise<T> {
	private Result<T> result;

	private List<Consumer<? super T>> fulfilledHandlers;
	private List<Consumer<? super Exception>> rejectedHandlers;

	public Promise(
			Consumer<? super Tuple3<? extends Consumer<? super T>, Consumer<? super Exception>, Consumer<? super Promise<T>>>> executor) {
		fulfilledHandlers = new ArrayList<Consumer<? super T>>();
		rejectedHandlers = new ArrayList<Consumer<? super Exception>>();

		executor.accept(new Tuple3<Consumer<? super T>, Consumer<? super Exception>, Consumer<? super Promise<T>>>(
				new Consumer<T>() {
					@Override
					public void accept(T t) {
						_fulfill(t);
					}
				}, new Consumer<Exception>() {
					@Override
					public void accept(Exception t) {
						_reject(t);
					}
				}, new Consumer<Promise<T>>() {
					@Override
					public void accept(Promise<T> t) {
						_resolve(t);
					}
				}));
	}

	private Promise() {
		this(
				new Consumer<Tuple3<? extends Consumer<? super T>, Consumer<? super Exception>, Consumer<? super Promise<T>>>>() {
					@Override
					public void accept(
							Tuple3<? extends Consumer<? super T>, Consumer<? super Exception>, Consumer<? super Promise<T>>> t) {
					}
				});
	}

	public boolean isSettled() {
		return result != null;
	}

	private void _fulfill(T value) {
		if (isSettled()) {
			throw new IllegalStateException();
		}

		result = Result.of(value);

		for (Consumer<? super T> handler : fulfilledHandlers) {
			handler.accept(value);
		}

		clearHandlers();
	}

	private void _reject(Exception reason) {
		if (isSettled()) {
			throw new IllegalStateException();
		}

		result = Result.of(reason);

		for (Consumer<? super Exception> handler : rejectedHandlers) {
			handler.accept(reason);
		}

		clearHandlers();
	}

	private void _resolve(Promise<T> promise) {
		promise.defer(new Consumer<T>() {
			@Override
			public void accept(T t) {
				_fulfill(t);
			}
		}, new Consumer<Exception>() {
			@Override
			public void accept(Exception t) {
				_reject(t);
			}
		});
	}

	private void clearHandlers() {
		fulfilledHandlers.clear();
		rejectedHandlers.clear();
	}

	private void defer(final Consumer<? super T> fulfilledHandler,
			final Consumer<? super Exception> rejectedHandler) {
		if (this.result != null) {
			Result<T> result = this.result;

			result.ifPresent(new Consumer<T>() {
				@Override
				public void accept(T t) {
					fulfilledHandler.accept(t);
				}
			}, new Consumer<Exception>() {
				@Override
				public void accept(Exception t) {
					rejectedHandler.accept(t);
				}
			});

			return;
		}

		fulfilledHandlers.add(fulfilledHandler);
		rejectedHandlers.add(rejectedHandler);
	}

	public <U> Promise<U> then(
			final Function<? super T, ? extends Promise<U>> onFulfilled) {
		return then(onFulfilled, null);
	}

	public <U> Promise<U> then(
			final Function<? super T, ? extends Promise<U>> onFulfilled,
			final Function<? super Exception, ? extends Promise<U>> onRejectedOrNull) {
		if (onFulfilled == null) {
			throw new IllegalArgumentException("'onFulfilled' cannot be null.");
		}

		final Promise<U> promise = new Promise<U>();

		defer(new Consumer<T>() {
			@Override
			public void accept(T t) {
				promise._resolve(onFulfilled.apply(t));
			}
		}, new Consumer<Exception>() {
			@Override
			public void accept(Exception t) {
				if (onRejectedOrNull != null) {
					Function<? super Exception, ? extends Promise<U>> onRejected = onRejectedOrNull;
					Promise<U> recoveryOrNull = onRejected.apply(t);
					if (recoveryOrNull != null) {
						Promise<U> recovery = recoveryOrNull;
						promise._resolve(recovery);
						return;
					}
				}

				promise._reject(t);
			}
		});

		return promise;
	}

	public Promise<T> catch_(
			final Function<? super Exception, ? extends Promise<T>> onRejected) {
		if (onRejected == null) {
			throw new IllegalArgumentException("'onRejected' cannot be null.");
		}

		final Promise<T> promise = new Promise<T>();

		defer(new Consumer<T>() {
			@Override
			public void accept(T t) {
				promise._fulfill(t);
			}
		}, new Consumer<Exception>() {
			@Override
			public void accept(Exception t) {
				Promise<T> recoveryOrNull = onRejected.apply(t);
				if (recoveryOrNull != null) {
					Promise<T> recovery = recoveryOrNull;
					promise._resolve(recovery);
					return;
				}

				promise._reject(t);
			}
		});

		return promise;
	}

	public Promise<T> finally_(final Supplier<? extends Promise<T>> onSettled) {
		if (onSettled == null) {
			throw new IllegalArgumentException("'onSettled' cannot be null.");
		}

		final Promise<T> promise = new Promise<T>();

		defer(new Consumer<T>() {
			@Override
			public void accept(T t) {
				Promise<T> updateOrNull = onSettled.get();
				if (updateOrNull != null) {
					Promise<T> update = updateOrNull;
					promise._resolve(update);
					return;
				}

				promise._fulfill(t);
			}
		}, new Consumer<Exception>() {
			@Override
			public void accept(Exception t) {
				Promise<T> recoveryOrNull = onSettled.get();
				if (recoveryOrNull != null) {
					Promise<T> recovery = recoveryOrNull;
					promise._resolve(recovery);
					return;
				}

				promise._reject(t);
			}
		});

		return promise;
	}

	private static class Result<T> {
		private boolean hasValue;
		private T value;
		private Exception reason;

		private Result(boolean hasValue, T value, Exception reason) {
			super();
			this.hasValue = hasValue;
			this.value = value;
			this.reason = reason;
		}

		public static <T> Result<T> of(T value) {
			return new Result<T>(true, value, null);
		}

		public static <T> Result<T> of(Exception reason) {
			if (reason == null) {
				throw new IllegalArgumentException();
			}
			return new Result<T>(false, null, reason);
		}

		public void ifPresent(Consumer<? super T> ifPresent,
				Consumer<? super Exception> orElse) {
			if (hasValue) {
				ifPresent.accept(value);
			} else {
				orElse.accept(reason);
			}
		}
	}

	// Extensions

	public static <T> Tuple4<Promise<T>, Consumer<? super T>, Consumer<? super Exception>, Consumer<? super Promise<T>>> deferred() {
		final Promise<T> promise = new Promise<T>();
		return new Tuple4<Promise<T>, Consumer<? super T>, Consumer<? super Exception>, Consumer<? super Promise<T>>>(
				promise, new Consumer<T>() {
					@Override
					public void accept(T t) {
						promise._fulfill(t);
					}
				}, new Consumer<Exception>() {
					@Override
					public void accept(Exception t) {
						promise._reject(t);
					}
				}, new Consumer<Promise<T>>() {
					@Override
					public void accept(Promise<T> t) {
						promise._resolve(t);
					}
				});
	}

	public static <T> Promise<T> fulfill(final T value) {
		return new Promise<T>(
				new Consumer<Tuple3<? extends Consumer<? super T>, Consumer<? super Exception>, Consumer<? super Promise<T>>>>() {
					@Override
					public void accept(
							Tuple3<? extends Consumer<? super T>, Consumer<? super Exception>, Consumer<? super Promise<T>>> executor) {
						executor.get1().accept(value);
					}
				});
	}

	public static <T> Promise<T> reject(final Exception reason) {
		return new Promise<T>(
				new Consumer<Tuple3<? extends Consumer<? super T>, Consumer<? super Exception>, Consumer<? super Promise<T>>>>() {
					@Override
					public void accept(
							Tuple3<? extends Consumer<? super T>, Consumer<? super Exception>, Consumer<? super Promise<T>>> executor) {
						executor.get2().accept(reason);
					}
				});
	}

	public static <T> Promise<T> resolve(final Promise<T> promise) {
		return new Promise<T>(
				new Consumer<Tuple3<? extends Consumer<? super T>, Consumer<? super Exception>, Consumer<? super Promise<T>>>>() {
					@Override
					public void accept(
							Tuple3<? extends Consumer<? super T>, Consumer<? super Exception>, Consumer<? super Promise<T>>> executor) {
						executor.get3().accept(promise);
					}
				});
	}

	public Promise<Void> then(final Consumer<? super T> onFulfilled) {
		return then(onFulfilled, null);
	}

	public Promise<Void> then(
			final Consumer<? super T> onFulfilled,
			final Function<? super Exception, ? extends Promise<Void>> onRejectedOrNull) {
		if (onFulfilled == null) {
			throw new IllegalArgumentException("'onFulfilled' cannot be null.");
		}

		return then(new Function<T, Promise<Void>>() {
			@Override
			public Promise<Void> apply(T value) {
				onFulfilled.accept(value);
				return Promise.fulfill(null);
			}
		}, new Function<Exception, Promise<Void>>() {
			@Override
			public Promise<Void> apply(Exception reason) {
				return onRejectedOrNull == null ? null : onRejectedOrNull
						.apply(reason);
			}
		});
	}

	public Promise<T> catch_(final Consumer<? super Exception> onRejected) {
		if (onRejected == null) {
			throw new IllegalArgumentException("'onRejected' cannot be null.");
		}

		return catch_(new Function<Exception, Promise<T>>() {
			@Override
			public Promise<T> apply(Exception reason) {
				onRejected.accept(reason);
				return null;
			}
		});
	}

	public Promise<T> finally_(final Runnable onSettled) {
		if (onSettled == null) {
			throw new IllegalArgumentException("'onSettled' cannot be null.");
		}

		return finally_(new Supplier<Promise<T>>() {
			@Override
			public Promise<T> get() {
				onSettled.run();
				return null;
			}
		});
	}
}
