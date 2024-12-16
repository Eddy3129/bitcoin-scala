import java.security.MessageDigest

// Transaction Case Class
case class Transaction(sender: String, recipient: String, amount: Double)

// Block Case Class (with a proof-of-work nonce and a timestamp)
case class Block(index: Int, previousHash: String, transactions: List[Transaction], timestamp: Long, nonce: Int, currentHash: String)

object Block {
  def createBlock(index: Int, previousHash: String, transactions: List[Transaction], difficulty: Int): Block = {
    var nonce = 0
    var currentHash = ""
    var hashFound = false
    val timestamp = System.currentTimeMillis()

    // Mining loop: Find a valid hash that meets the difficulty
    while (!hashFound) {
      val blockData = s"$index$previousHash${transactions.mkString("")}$timestamp$nonce"
      currentHash = calculateHash(blockData)
      if (currentHash.startsWith("0" * difficulty)) {
        hashFound = true
      } else {
        nonce += 1
      }
    }

    Block(index, previousHash, transactions, timestamp, nonce, currentHash)
  }

  def calculateHash(data: String): String = {
    val md = MessageDigest.getInstance("SHA-256")
    md.update(data.getBytes)
    md.digest().map("%02x".format(_)).mkString
  }
}
