from iconservice import *

TAG = 'HelloWorld'


class HelloWorld(IconScoreBase):
    _NAME = 'name'

    def __init__(self, db: IconScoreDatabase) -> None:
        super().__init__(db)
        self._name = VarDB(self._NAME, db, value_type=str)

    def on_install(self, name: str) -> None:
        super().on_install()
        self._name.set(name)
        Logger.info(f"on_install: name={name}", TAG)

    def on_update(self, name: str) -> None:
        super().on_update()
        self._name.set(name)
        Logger.info(f"on_update: name={name}", TAG)

    @external(readonly=True)
    def name(self) -> str:
        return self._name.get()

    @external(readonly=True)
    def hello(self) -> str:
        Logger.info('Hello, world!', TAG)
        return "Hello, world!"

    @payable
    def fallback(self):
        Logger.info('fallback is called', TAG)

    @external
    def tokenFallback(self, _from: Address, _value: int, _data: bytes):
        Logger.info('tokenFallback is called', TAG)

    @external
    def transferICX(self, to: Address, amount: int):
        if amount > 0:
            self.icx.transfer(to, amount)
