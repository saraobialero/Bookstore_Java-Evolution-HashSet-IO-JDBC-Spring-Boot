package org.evpro.bookshopV4;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BookshopApplication {

        private static final Logger logger = LoggerFactory.getLogger(BookshopApplication.class);

        public static void main(String[] args) {
            logger.info("Test log message");
            logger.error("Test error message");
        }
    }

