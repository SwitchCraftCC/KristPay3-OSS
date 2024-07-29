# KristPay3

## Commands

### For Players
- `/bal[ance]` (kristpay.balance.get.base)<br />View your own Krist balance
- `/txs` `/transactions` (kristpay.list.base)<br />View your own transaction history
- `/pay <address> <amount>` (kristpay.pay.address)<br />Pay another player or Krist address
- `/deposit` (no permission node)<br />View your own deposit address
- `/welfare` (kristpay.welfare.check.base)<br />Check whether you are opted into welfare
- `/welfare opt in` (kristpay.welfare.opt.in.base)<br />Opt into welfare
- `/welfare opt out` (kristpay.welfare.opt.out.base)<br />Opt out of welfare
- `/welfare return` (kristpay.welfare.return.base)<br />Return your welfare rewards to the master wallet

### For Staff
- `/bal[ance] <player>` (kristpay.balance.get.others)<br />View another player's Krist balance
- `/setbal [player] <amount>` (kristpay.balance.set)<br />Set a player's Krist balance
- `/grant <player> <amount>` (kristpay.balance.grant)<br />Grant a player Krist (semantically equivalent to `/setbal oldBal+delta`)
- `/txs <player>` `/transactions <player>` (kristpay.list.others)<br />View another player's transaction history
- `/masterbal` (kristpay.masterbal.check)<br />View the master wallet and the allocated / unallocated amounts
- `/welfare <player>` (kristpay.welfare.check.others)<br />Check whether a player is opted into welfare
- `/welfare return <player>` (kristpay.welfare.return.others)<br />Return a player's welfare rewards to the master wallet

## Permissions
Permission | Role | Description
-|-|-
kristpay.balance.get.base | USER | Allows the user to view their own Krist balance
kristpay.balance.get.others | MOD | Allows the user to view another user's Krist balance
kristpay.balance.set.base | ADMIN | Set your Krist balance
kristpay.balance.set.others | ADMIN | Set another user's Krist balance
kristpay.grant.base | ADMIN | Grant yourself Krist
kristpay.grant.others | ADMIN | Grant another user Krist
kristpay.list.base | USER | List your own transactions
kristpay.list.others | MOD | List another user's transactions
kristpay.masterbal.check | MOD | Allow the user to check the master wallet balance
kristpay.pay.address | USER | Allow the user to pay another user or address
kristpay.welfare.check.base | USER | Allow the user to check whether they are opted into welfare
kristpay.welfare.check.others | MOD | Check whether another user is opted into welfare
kristpay.welfare.claim | NONE | Allow the user to claim all types of welfare
kristpay.welfare.claim.faucet | USER | Allow the user to run /faucet
kristpay.welfare.claim.login | USER | Allow the user to claim the daily login bonus
kristpay.welfare.opt.in | USER | Allow the user to opt in to welfare
kristpay.welfare.opt.out | USER | Allow the user to opt out of welfare
kristpay.welfare.return.base | USER | Allows the user to return the welfare rewards they have received back to the server
kristpay.welfare.return.others | ADMIN | Force another user to return the welfare rewards they have received

## API Usage

![](https://maven.pkg.github.com/SwitchCraftCC/KristPay3-OSS/api/badge/latest/releases/io/sc3/kristpay-api?name=Latest%20version)
```properties
# gradle.properties
kristpayVersion = <version>
```

```kotlin
// build.gradle.kts
val kristpayVersion: String by project

repositories {
  maven {
    url = uri("https://maven.pkg.github.com/SwitchCraftCC/KristPay3-OSS/releases")
    content {
      includeGroup("io.sc3")
    }
  }
}

dependencies {
  compileOnly("io.sc3", "kristpay-api", kristpayVersion)
}
```
