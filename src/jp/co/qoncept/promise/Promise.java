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

	private List<Consumer<T>> fulfilledHandlers;
	private List<Consumer<Exception>> rejectedHandlers;

	public Promise(
			Consumer<Tuple3<Consumer<T>, Consumer<Exception>, Consumer<Promise<T>>>> executor) {
		fulfilledHandlers = new ArrayList<Consumer<T>>();
		rejectedHandlers = new ArrayList<Consumer<Exception>>();

		executor.accept(new Tuple3<Consumer<T>, Consumer<Exception>, Consumer<Promise<T>>>(
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
				new Consumer<Tuple3<Consumer<T>, Consumer<Exception>, Consumer<Promise<T>>>>() {
					@Override
					public void accept(
							Tuple3<Consumer<T>, Consumer<Exception>, Consumer<Promise<T>>> t) {
					}
				});
	}

	private void _fulfill(T value) {
		if (result != null) {
			throw new IllegalStateException();
		}

		result = Result.of(value);

		for (Consumer<T> handler : fulfilledHandlers) {
			handler.accept(value);
		}

		clearHandlers();
	}

	private void _reject(Exception reason) {
		if (result != null) {
			throw new IllegalStateException();
		}

		result = Result.of(reason);

		for (Consumer<Exception> handler : rejectedHandlers) {
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

	private void defer(final Consumer<T> fulfilledHandler,
			final Consumer<Exception> rejectedHandler) {
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

	public <U> Promise<U> then(final Function<T, Promise<U>> onFulfilled) {
		return then(onFulfilled, null);
	}

	public <U> Promise<U> then(final Function<T, Promise<U>> onFulfilled,
			final Function<Exception, Promise<U>> onRejectedOrNull) {
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
					Function<Exception, Promise<U>> onRejected = onRejectedOrNull;
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

	public Promise<T> catch_(final Function<Exception, Promise<T>> onRejected) {
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

	public Promise<T> finally_(final Supplier<Promise<T>> onSettled) {
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
		private T value;
		private Exception reason;

		private Result(T value, Exception reason) {
			super();
			this.value = value;
			this.reason = reason;
		}

		public static <T> Result<T> of(T value) {
			if (value == null) {
				throw new IllegalArgumentException();
			}
			return new Result<T>(value, null);
		}

		public static <T> Result<T> of(Exception reason) {
			if (reason == null) {
				throw new IllegalArgumentException();
			}
			return new Result<T>(null, reason);
		}

		public void ifPresent(Consumer<T> ifPresent, Consumer<Exception> orElse) {
			if (value != null) {
				ifPresent.accept(value);
			} else {
				orElse.accept(reason);
			}
		}
	}

	// Extensions

	public static <T> Tuple4<Promise<T>, Consumer<T>, Consumer<Exception>, Consumer<Promise<T>>> deferred() {
		final Promise<T> promise = new Promise<T>();
		return new Tuple4<Promise<T>, Consumer<T>, Consumer<Exception>, Consumer<Promise<T>>>(
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
				new Consumer<Tuple3<Consumer<T>, Consumer<Exception>, Consumer<Promise<T>>>>() {
					@Override
					public void accept(
							Tuple3<Consumer<T>, Consumer<Exception>, Consumer<Promise<T>>> executor) {
						executor.get1().accept(value);
					}
				});
	}

	public static <T> Promise<T> reject(final Exception reason) {
		return new Promise<T>(
				new Consumer<Tuple3<Consumer<T>, Consumer<Exception>, Consumer<Promise<T>>>>() {
					@Override
					public void accept(
							Tuple3<Consumer<T>, Consumer<Exception>, Consumer<Promise<T>>> executor) {
						executor.get2().accept(reason);
					}
				});
	}

	public static <T> Promise<T> resolve(final Promise<T> promise) {
		return new Promise<T>(
				new Consumer<Tuple3<Consumer<T>, Consumer<Exception>, Consumer<Promise<T>>>>() {
					@Override
					public void accept(
							Tuple3<Consumer<T>, Consumer<Exception>, Consumer<Promise<T>>> executor) {
						executor.get3().accept(promise);
					}
				});
	}

	public Promise<Void> then(final Consumer<T> onFulfilled) {
		return then(onFulfilled, null);
	}

	public Promise<Void> then(final Consumer<T> onFulfilled,
			final Function<Exception, Promise<Void>> onRejectedOrNull) {
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

	public Promise<T> catch_(final Consumer<Exception> onRejected) {
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