CREATE TABLE `properties` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `key` varchar(50) DEFAULT NULL,
  `value` varchar(500) DEFAULT NULL,
  `application` varchar(50) DEFAULT NULL,
  `profile` varchar(50) DEFAULT NULL,
  `label` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

/*
The label shall be 'master' instead of null, since null label will be converted to 'master' in
org.springframework.cloud.config.server.environment.JdbcEnvironmentRepository.
*/
INSERT INTO `properties` VALUES (null, 'book.name', 'deep in spring cloud', 'book', 'prod', 'master');
INSERT INTO `properties` VALUES (null, 'book.author', 'jim', 'book', 'prod', 'master');
INSERT INTO `properties` VALUES (null, 'book.category', 'spring cloud', 'book', 'prod', 'master');
