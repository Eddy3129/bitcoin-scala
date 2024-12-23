// src/main/scala/blockchainapp/models/Block.scala

package blockchainapp.models

import java.security.MessageDigest

case class Block(
                  index: Int,
                  previousHash: String,
                  transactions: List[Transaction],
                  nonce: Int,
                  currentHash: String
                )

object Block {
  def genesis: Block = Block(
    index = 0,
    previousHash = "0",
    transactions = List(),
    nonce = 0,
    currentHash = "0"
  )

  def createBlock(index: Int, previousHash: String, transactions: List[Transaction], difficulty: Int): Block = {
    var nonce = 0
    var currentHash = ""
    var hashFound = false

    while (!hashFound) {
      val blockData = s"$index$previousHash${transactions.mkString("")}$nonce"
      currentHash = calculateHash(blockData)
      if (currentHash.startsWith("0" * difficulty)) {
        hashFound = true
      } else {
        nonce += 1
        if (nonce % 1000 == 0) {
          Thread.sleep(10) // Simulate mining delay
        }
      }
    }

    Block(index, previousHash, transactions, nonce, currentHash)
  }

  def calculateHash(data: String): String = {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(data.getBytes)
    md.digest().map("%02x".format(_)).mkString
  }
}
