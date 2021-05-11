package com.tencent.qqmusic.api.demo;

import com.tencent.qqmusic.third.api.contract.CommonCmd;

public class Config {

    public static String OPENID_APPID = "2";

    public static String OPENID_APP_PRIVATE_KEY = "MIICdQIBADANBgkqhkiG9w0BAQEFAASCAl8wggJbAgEAAoGBAN+qC0FoQFl/3SGXq3zFlB3U9jkMzcOy4jNGrd3+Mv6SfJLWyh4QhVR1lV1GbBjSoG0P6ilztRH1n1HAwQGMOAtj7DxnMku4H0XLkVkDFhescR3QfNS5oZzpsq3JRoXnBNTCDVi956vvZ5kKCoHNIQyrKwELJ3rJfA6v2NiynvczAgMBAAECgYAcXfy/dvFyaH0rXYkqcgSvI+t2oOEYCQAXcMdseGkPUJTsKsHHvmCqrZ8cDWp4W35tVq9kQoCcnoJuY/wWrioNSxOFYvD2aOMPl377vemPjnkmCNnZxysxfRmkN6y3FaYuG6jOgFnI5DAcfdkd6jDWTbd6AWzpyXI9XQgz30pLAQJBAPHamKHfQ9HADaqq5Hvu/AVqBBkJ7m90/0PpslYP8nqP6nQkcwi59Sf2gRNWQlN6NoAG2dqPxFnk0mReFa0eRlECQQDsvxWmk4srUOnxoxc9z7ed47pNQgiBtJpve5MvrGONEIS8FHEhFsLqJui4McqViETXruEv4wJf8bKVt9a/lZBDAkAtB5Ang1QzN1jUD+Femc5ei7CboNe99MCaOmaz02BIJYd3fFnWpBjbCfBaU1MiC70d9SiWovHh8tKhUUsj4mEhAkBWiBlPw0nw0ShRC71o+E0yxpPHvUUCs5JnARHxMN9KJil93TLkVz9y+jnBaWGUejQ/aUohiKXLj7oogwNZDz+lAkAGkvo2LdnfeM6RIntn6RbC/EJoBk2L2whj2qlW5UubyYiX+X+a2okgcfNvwYjjNlfR27KNGOaK8ZUy8882PJ3Y";

    /**
     * see {@link CommonCmd#AIDL_PLATFORM_TYPE_PHONE }
     * {@link CommonCmd#AIDL_PLATFORM_TYPE_TV}
     * {@link CommonCmd#AIDL_PLATFORM_TYPE_CAR}
     * {@link CommonCmd#AIDL_PLATFORM_TYPE_PAD}
     */
    public static String BIND_PLATFORM = CommonCmd.AIDL_PLATFORM_TYPE_PHONE;

}