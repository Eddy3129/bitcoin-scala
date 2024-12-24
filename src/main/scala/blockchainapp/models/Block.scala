// src/main/scala/blockchainapp/models/Block.scala

package blockchainapp.models

import java.security.MessageDigest

case class Block(index: Int, previousHash: String, transactions: List[Transaction], nonce: Long, currentHash: String)

object Block {
  def genesis: Block = Block(0, "0", List(), 0, calculateHash("0"))

  def createBlock(index: Int, previousHash: String, transactions: List[Transaction], difficulty: Int): Block = {
    var nonce = 0L
    var hash = ""
    do {
      nonce += 1
      hash = calculateHash(s"$index$previousHash${transactions.mkString}$nonce")
    } while (!hash.startsWith("0" * difficulty))
    Block(index, previousHash, transactions, nonce, hash)
  }

  def calculateHash(data: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(data.getBytes("UTF-8"))
    hashBytes.map("%02x".format(_)).mkString
  }
}
